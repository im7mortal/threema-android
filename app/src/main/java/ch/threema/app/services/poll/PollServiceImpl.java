package ch.threema.app.services.poll;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import ch.threema.app.R;
import ch.threema.app.eventbus.GlobalEventBuses;
import ch.threema.app.eventbus.events.PollEvent;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.PollUtil;
import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.CollectionExtensionsKt.hasDuplicatesBy;
import static ch.threema.common.JavaCompat.areEqual;
import static ch.threema.common.JavaCompat.toHexString;

import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.poll.PollSetupInterface;
import ch.threema.domain.protocol.csp.messages.poll.PollSetupMessage;
import ch.threema.domain.protocol.csp.messages.poll.PollData;
import ch.threema.domain.protocol.csp.messages.poll.PollDataChoice;
import ch.threema.domain.protocol.csp.messages.poll.PollId;
import ch.threema.domain.protocol.csp.messages.poll.PollVote;
import ch.threema.domain.protocol.csp.messages.poll.PollVoteInterface;
import ch.threema.domain.protocol.csp.messages.poll.GroupPollSetupMessage;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.GroupPollModelFactory;
import ch.threema.storage.factories.IdentityPollModelFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.poll.PollChoiceModel;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.poll.PollVoteModel;
import ch.threema.storage.models.poll.GroupPollModel;
import ch.threema.storage.models.poll.IdentityPollModel;
import ch.threema.storage.models.poll.LinkPollModel;

public class PollServiceImpl implements PollService {
    private static final Logger logger = getThreemaLogger("PollServiceImpl");

    private static final int REQUIRED_CHOICE_COUNT = 2;

    private final SparseArray<PollModel> pollModelCache;
    private final SparseArray<LinkPollModel> linkPollModelCache;

    private final DatabaseService databaseService;
    private final UserService userService;
    private final GroupService groupService;
    private final ContactService contactService;
    private final ServiceManager serviceManager;
    @NonNull
    private final GlobalEventBuses globalEventBuses;

    private int openPollId = 0;

    public PollServiceImpl(
        SparseArray<PollModel> pollModelCache,
        SparseArray<LinkPollModel> linkPollModelCache,
        DatabaseService databaseService,
        UserService userService,
        GroupService groupService,
        ContactService contactService,
        ServiceManager serviceManager,
        @NonNull
        GlobalEventBuses globalEventBuses
    ) {
        this.pollModelCache = pollModelCache;
        this.linkPollModelCache = linkPollModelCache;
        this.databaseService = databaseService;
        this.userService = userService;
        this.groupService = groupService;
        this.contactService = contactService;
        this.serviceManager = serviceManager;
        this.globalEventBuses = globalEventBuses;
    }

    @Override
    public PollModel create(
        GroupModelOld groupModel,
        String description,
        PollModel.State state,
        PollModel.Assessment assessment,
        PollModel.Type type,
        PollModel.ChoiceType choiceType,
        @NonNull PollId pollId
    ) throws NotAllowedException {

        final PollModel model = this.create(description, state, assessment, type, choiceType, pollId);
        if (model != null) {
            this.link(groupModel, model);
            //handle
        }


        return model;
    }

    @Override
    public void modifyFinished(
        @NonNull final PollModel pollModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException {
        if (pollModel.getState() == PollModel.State.TEMPORARY) {
            pollModel.setState(PollModel.State.OPEN);
            try {
                this.checkAccess();
                this.databaseService.getPollModelFactory().update(pollModel);
            } catch (NotAllowedException e) {
                logger.error("Exception", e);
                return;
            }

            try {
                var sent = send(pollModel, messageId, triggerSource);
                if (sent) {
                    globalEventBuses.getPolls().emit(new PollEvent.NewPoll(pollModel));
                }
            } catch (MessageTooLongException e) {
                pollModel.setState(PollModel.State.TEMPORARY);
                this.databaseService.getPollModelFactory().update(
                    pollModel
                );
                throw e;
            }
        } else {
            globalEventBuses.getPolls().emit(new PollEvent.PollUpdated(pollModel));
        }
    }

    @Override
    public boolean viewingPoll(PollModel pollModel, boolean view) {
        if (pollModel != null) {
            if (view) {
                pollModel.setLastViewedAt(Instant.now());
                this.databaseService.getPollModelFactory().update(
                    pollModel);
                this.openPollId = pollModel.getId();
                return true;
            } else if (this.openPollId == pollModel.getId()) {
                this.openPollId = 0;
            }

        }
        return false;
    }

    @Override
    public PollModel create(
        ContactModel contactModel,
        String description,
        PollModel.State state,
        PollModel.Assessment assessment,
        PollModel.Type type,
        PollModel.ChoiceType choiceType,
        @NonNull PollId pollId
    ) throws NotAllowedException {
        final PollModel model = this.create(description, state, assessment, type, choiceType, pollId);
        if (model != null) {
            this.link(contactModel, model);
        }

        return model;
    }

    private PollModel create(
        String description,
        PollModel.State state,
        PollModel.Assessment assessment,
        PollModel.Type type,
        PollModel.ChoiceType choiceType,
        PollId newPollId
    ) throws NotAllowedException {
        //create a new blank model
        try {
            this.checkAccess();


            final PollModel pollModel = new PollModel();
            pollModel.setApiPollId(toHexString(newPollId.getPollId()));
            pollModel.setCreatorIdentity(this.userService.getIdentity());
            pollModel.setCreatedAt(Instant.now());
            pollModel.setModifiedAt(Instant.now());
            pollModel.setName(description);
            pollModel.setState(state);
            pollModel.setAssessment(assessment);
            pollModel.setType(type);
            pollModel.setChoiceType(choiceType);
            pollModel.setDisplayType(PollModel.DisplayType.LIST_MODE); // default display type for polls created on mobile client.
            pollModel.setLastViewedAt(Instant.now());

            this.databaseService.getPollModelFactory().create(
                pollModel
            );

            this.cache(pollModel);

            return pollModel;

        } catch (NotAllowedException notAllowedException) {
            logger.error("Not allowed", notAllowedException);
            throw notAllowedException;
        }
    }

    @Override
    public boolean update(PollModel pollModel, PollChoiceModel choice) throws NotAllowedException {
        if (choice.getId() > 0 && choice.getPollId() > 0 && choice.getPollId() != pollModel.getId()) {
            throw new NotAllowedException("choice already set on another poll");
        }

        choice.setPollId(pollModel.getId());

        if (choice.getCreatedAt() == null) {
            choice.setCreatedAt(Instant.now());
        }

        choice.setModifiedAt(Instant.now());

        return this.databaseService.getPollChoiceModelFactory().create(
            choice
        );
    }

    @Override
    public boolean close(Integer pollModelId, @NonNull MessageId messageId, @NonNull TriggerSource triggerSource) throws NotAllowedException, MessageTooLongException {
        //be sure to use the cached poll model!
        final PollModel pollModel = this.get(pollModelId);

        MessageReceiver messageReceiver = this.getReceiver(pollModel);
        if (messageReceiver == null) {
            return false;
        }

        if (!PollUtil.canClose(pollModel, this.userService.getIdentity(), messageReceiver)) {
            throw new NotAllowedException();
        }

        //save model
        pollModel.setState(PollModel.State.CLOSED);
        if (this.update(pollModel)) {
            var sent = send(pollModel, messageId, triggerSource);
            if (sent) {
                globalEventBuses.getPolls().emit(new PollEvent.PollClosed(pollModel));
            }
            return sent;
        }
        return false;
    }

    private boolean send(
        PollModel pollModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException {
        var myIdentity = userService.getIdentity();
        if (myIdentity != null && myIdentity.equals(pollModel.getCreatorIdentity())) {
            try {
                return serviceManager.getMessageService().sendPollMessage(pollModel, messageId, triggerSource) != null;
            } catch (ThreemaException e) {
                logger.error("Exception", e);
                if (e instanceof MessageTooLongException) {
                    throw (MessageTooLongException) e;
                }
            }
        }
        return false;
    }

    @Override
    @Nullable
    public PollModel get(int pollId) {
        PollModel model = this.getFromCache(pollId);
        if (model == null) {
            model = this.databaseService.getPollModelFactory().getById(pollId);

            this.cache(model);
        }
        return model;
    }

    @Override
    @NonNull
    public PollUpdateResult update(
        @NonNull PollSetupInterface createMessage,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException, BadMessageException {
        //check if allowed
        PollData pollData = createMessage.getPollData();
        if (pollData == null) {
            throw new ThreemaException("invalid format");
        }

        final PollModel.State toState;
        final PollModel pollModel;

        Instant date = ((AbstractMessage) createMessage).getTimestamp();
        PollModel existingModel = this.get(createMessage.getPollId().toString(), createMessage.getPollCreatorIdentity());

        if (existingModel != null) {
            if (pollData.getDisplayType() != null && existingModel.getDisplayType() != null && pollData.getDisplayType().ordinal() != existingModel.getDisplayType().ordinal()) {
                throw new BadMessageException("Poll display mode not allowed to change. Discarding message");
            }
            if (pollData.getState() == PollData.State.CLOSED) {
                pollModel = existingModel;
                toState = PollModel.State.CLOSED;
            } else {
                throw new BadMessageException("Poll with same ID already exists. Discarding message.");
            }
        } else {
            if (pollData.getState() != PollData.State.CLOSED) {
                pollModel = new PollModel();
                pollModel.setCreatorIdentity(createMessage.getPollCreatorIdentity());
                pollModel.setApiPollId(createMessage.getPollId().toString());
                pollModel.setCreatedAt(date);
                pollModel.setLastViewedAt(null);
                toState = PollModel.State.OPEN;
            } else {
                throw new BadMessageException("New poll with closed state requested. Discarding message.");
            }
        }

        pollModel.setName(pollData.getDescription());
        pollModel.setModifiedAt(Instant.now());

        switch (pollData.getAssessmentType()) {
            case MULTIPLE:
                pollModel.setAssessment(PollModel.Assessment.MULTIPLE_CHOICE);
                break;
            case SINGLE:
                pollModel.setAssessment(PollModel.Assessment.SINGLE_CHOICE);
                break;
        }

        switch (pollData.getType()) {
            case RESULT_ON_CLOSE:
                pollModel.setType(PollModel.Type.RESULT_ON_CLOSE);
                break;
            case INTERMEDIATE:
                pollModel.setType(PollModel.Type.INTERMEDIATE);
                break;
        }

        switch (pollData.getChoiceType()) {
            case TEXT:
                pollModel.setChoiceType(PollModel.ChoiceType.TEXT);
                break;
        }

        switch (pollData.getDisplayType()) {
            case SUMMARY_MODE:
                pollModel.setDisplayType(PollModel.DisplayType.SUMMARY_MODE);
                break;
            case LIST_MODE:
            default:
                pollModel.setDisplayType(PollModel.DisplayType.LIST_MODE);
                break;

        }

        pollModel.setState(toState);

        if (toState == PollModel.State.OPEN) {
            this.databaseService.getPollModelFactory().create(
                pollModel
            );
        } else {
            this.databaseService.getPollModelFactory().update(
                pollModel
            );
        }

        if (createMessage instanceof GroupPollSetupMessage) {
            GroupModelOld groupModel;
            groupModel = this.groupService.getByGroupMessage((GroupPollSetupMessage) createMessage);
            if (groupModel == null) {
                throw new ThreemaException("invalid group");
            }
            //link with group
            this.link(groupModel, pollModel);
        } else if (createMessage instanceof PollSetupMessage) {
            ContactModel contactModel = this.contactService.getByIdentity(createMessage.getPollCreatorIdentity());
            if (contactModel == null) {
                throw new ThreemaException("invalid identity");
            }
            //link with contact
            this.link(contactModel, pollModel);
        } else {
            throw new ThreemaException("invalid");
        }

        if (toState == PollModel.State.CLOSED && pollModel.getDisplayType() == PollModel.DisplayType.LIST_MODE) {
            //first remove all previously known votes if result should be shown in list mode to ensure a common result for all participants
            this.databaseService.getPollVoteModelFactory().deleteByPollId(
                pollModel.getId()
            );
        }

        //create choices of poll
        for (PollDataChoice apiChoice : pollData.getChoiceList()) {
            //check if choice already exist
            PollChoiceModel pollChoiceModel = this.getChoiceByApiId(pollModel, apiChoice.getId());
            if (pollChoiceModel == null) {
                pollChoiceModel = new PollChoiceModel();
                pollChoiceModel.setPollId(pollModel.getId());
                pollChoiceModel.setApiPollChoiceId(apiChoice.getId());
            }

            // save returned total vote count if poll is in summary mode (case broadcast poll)
            if (pollModel.getDisplayType() == PollModel.DisplayType.SUMMARY_MODE) {
                pollChoiceModel.setVoteCount(apiChoice.getTotalVotes());
            }

            pollChoiceModel.setName(apiChoice.getName());
            pollChoiceModel.setOrder(apiChoice.getOrder());
            switch (pollData.getChoiceType()) {
                case TEXT:
                    pollChoiceModel.setType(PollChoiceModel.Type.Text);
                    break;
            }
            pollChoiceModel.setCreatedAt(date);

            this.databaseService.getPollChoiceModelFactory().createOrUpdate(
                pollChoiceModel
            );

            //save individual votes received in case result should be shown in list mode for each participant (case mobile client user poll)
            if (pollModel.getDisplayType() == PollModel.DisplayType.LIST_MODE && !pollData.getParticipants().isEmpty()) {
                int participantPos = 0;
                for (String p : pollData.getParticipants()) {
                    PollVoteModel voteModel = new PollVoteModel();
                    voteModel.setPollId(pollModel.getId());
                    voteModel.setPollChoiceId(pollChoiceModel.getId());
                    voteModel.setVotingIdentity(p);
                    voteModel.setChoice(apiChoice.getResult(participantPos));
                    voteModel.setModifiedAt(Instant.now());
                    voteModel.setCreatedAt(Instant.now());

                    this.databaseService.getPollVoteModelFactory().create(
                        voteModel
                    );

                    participantPos++;
                }
            }
        }

        if (toState == PollModel.State.OPEN) {
            this.cache(pollModel);

            var sent = send(pollModel, messageId, triggerSource);
            if (sent) {
                globalEventBuses.getPolls().emit(new PollEvent.NewPoll(pollModel));
            }

            return new PollUpdateResult(pollModel, PollUpdateResult.Operation.CREATE);
        } else {
            var sent = send(pollModel, messageId, triggerSource);
            if (sent) {
                globalEventBuses.getPolls().emit(new PollEvent.PollClosed(pollModel));
            }
            return new PollUpdateResult(pollModel, PollUpdateResult.Operation.CLOSE);
        }
    }

    @Override
    public PollModel get(@NonNull String id, @Nullable String creator) {
        if (id.isEmpty() && (creator == null || creator.isEmpty())) {
            return null;
        }

        PollModel model = this.getFromCache(id, creator);
        if (model == null) {
            model = this.databaseService.getPollModelFactory().getByApiPollIdAndIdentity(id, creator);

            this.cache(model);
        }

        return model;
    }

    @Override
    public List<PollModel> getPolls(final PollFilter filter) {
        List<PollModel> polls = this.databaseService.getPollModelFactory().filter(
            filter
        );
        this.cache(polls);
        return polls;
    }

    @Override
    public long countPolls(final PollFilter filter) {
        return this.databaseService.getPollModelFactory().count(filter);
    }

    @Override
    public List<PollChoiceModel> getChoices(Integer pollModelId) throws NotAllowedException {
        if (pollModelId == null) {
            throw new NotAllowedException();
        }

        return this.databaseService.getPollChoiceModelFactory().getByPollId(
            pollModelId
        );
    }

    @Override
    public int getVotingCount(PollChoiceModel choiceModel) {
        PollModel b = this.get(choiceModel.getPollId());
        if (b == null) {
            return 0;
        }

        return this.getCalculatedVotingCount(choiceModel);
    }


    @Override
    public boolean update(final PollModel pollModel) {
        pollModel.setModifiedAt(Instant.now());
        databaseService.getPollModelFactory().update(pollModel);

        globalEventBuses.getPolls().emit(new PollEvent.PollUpdated(pollModel));
        return true;
    }

    @Override
    public boolean removeVotes(final MessageReceiver receiver, final String identity) {
        List<PollModel> polls = this.getPolls(new PollFilter() {
            @Override
            public MessageReceiver getReceiver() {
                return receiver;
            }

            @Override
            public PollModel.State[] getStates() {
                return new PollModel.State[0];
            }
        });

        for (final PollModel pollModel : polls) {
            this.databaseService.getPollVoteModelFactory().deleteByPollIdAndVotingIdentity(
                pollModel.getId(),
                identity
            );

            globalEventBuses.getPolls().emit(PollEvent.PollVoteRemoved.javaCreate(pollModel, identity));
        }

        return true;
    }

    @Override
    @NonNull
    public List<String> getVotedParticipants(Integer pollModelId) {
        List<String> identities = new ArrayList<>();

        if (pollModelId != null) {
            List<PollVoteModel> pollVotes = this.getPollVotes(pollModelId);
            for (PollVoteModel v : pollVotes) {
                if (!identities.contains(v.getVotingIdentity())) {
                    identities.add(v.getVotingIdentity());
                }
            }
        }
        return identities;
    }

    @Override
    @NonNull
    public List<String> getPendingParticipants(Integer pollModelId) {
        String[] allParticipants = this.getParticipants(pollModelId);
        List<String> pendingParticipants = new ArrayList<>();
        for (String i : allParticipants) {
            List<PollVoteModel> voteModels = this.getVotes(pollModelId, i);
            if (voteModels.isEmpty()) {
                pendingParticipants.add(i);
            }
        }

        return pendingParticipants;
    }

    @Override
    @NonNull
    public String[] getParticipants(Integer pollModelId) {
        PollModel b = this.get(pollModelId);
        if (b != null) {
            try {
                LinkPollModel link = this.getLinkedPollModel(b);
                if (link != null) {
                    switch (link.getType()) {
                        case GROUP:
                            GroupModelOld groupModel = this.getGroupModel(link);
                            if (groupModel != null) {
                                return this.groupService.getGroupMemberIdentities(this.getGroupModel(link));
                            }
                            break;
                        case CONTACT:
                            ContactModel contactModel = this.getContactModel(link);
                            if (contactModel != null) {
                                return new String[]{
                                    this.userService.getIdentity(),
                                    contactModel.getIdentity()};
                            }
                            break;

                        default:
                            throw new NotAllowedException("invalid type");
                    }
                }
            } catch (NotAllowedException e) {
                logger.error("Exception", e);
            }
        }

        return new String[0];
    }

    @NonNull
    private List<PollVoteModel> getVotes(Integer pollModelId, String fromIdentity) {
        if (pollModelId == null) {
            return Collections.emptyList();
        }

        return this.databaseService.getPollVoteModelFactory().getByPollIdAndVotingIdentity(
            pollModelId,
            fromIdentity
        );
    }

    @Override
    public boolean hasVoted(Integer pollModelId, String fromIdentity) {
        if (pollModelId == null) {
            return false;
        }

        return this.databaseService.getPollVoteModelFactory().countByPollIdAndVotingIdentity(
            pollModelId,
            fromIdentity
        ) > 0L;
    }

    @Override
    @NonNull
    public List<PollVoteModel> getMyVotes(Integer pollModelId) {
        return this.getVotes(pollModelId, this.userService.getIdentity());
    }

    @Override
    public List<PollVoteModel> getPollVotes(Integer pollModelId) {
        if (pollModelId == null) {
            return null;
        }
        return this.databaseService.getPollVoteModelFactory().getByPollId(pollModelId);
    }


    @Override
    public boolean removeAll() {
        this.databaseService.getPollModelFactory().deleteAll();
        this.databaseService.getPollVoteModelFactory().deleteAll();
        this.databaseService.getPollChoiceModelFactory().deleteAll();
        this.databaseService.getGroupPollModelFactory().deleteAll();
        return true;
    }

    @Override
    public PollPublishResult publish(
        MessageReceiver messageReceiver,
        final PollModel pollModel,
        AbstractMessageModel abstractMessageModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException, MessageTooLongException {
        return this.publish(messageReceiver, pollModel, abstractMessageModel, null, messageId, triggerSource);
    }

    @Override
    public PollPublishResult publish(
        MessageReceiver messageReceiver,
        final PollModel pollModel,
        AbstractMessageModel abstractMessageModel,
        @Nullable Collection<String> receivingIdentities,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException, MessageTooLongException {
        PollPublishResult result = new PollPublishResult();

        this.checkAccess();

        if (messageReceiver == null || pollModel == null) {
            return result;
        }

        // validate choices
        List<PollChoiceModel> choices = this.getChoices(pollModel.getId());
        if (choices == null || choices.size() < REQUIRED_CHOICE_COUNT) {
            return result.error(R.string.ballot_error_more_than_x_choices);
        }

        switch (messageReceiver.getType()) {
            case MessageReceiver.Type_GROUP:
                this.link(((GroupMessageReceiver) messageReceiver).getGroup(), pollModel);
                break;

            case MessageReceiver.Type_CONTACT:
                this.link(((ContactMessageReceiver) messageReceiver).getContact(), pollModel);
                break;
        }

        final boolean isClosing = pollModel.getState() == PollModel.State.CLOSED;

        PollData pollData = new PollData();
        pollData.setDescription(pollModel.getName());

        switch (pollModel.getChoiceType()) {
            case TEXT:
                pollData.setChoiceType(PollData.ChoiceType.TEXT);
                break;
        }

        switch (pollModel.getType()) {
            case RESULT_ON_CLOSE:
                pollData.setType(PollData.Type.RESULT_ON_CLOSE);
                break;
            case INTERMEDIATE:
            default:
                pollData.setType(PollData.Type.INTERMEDIATE);
        }

        switch (pollModel.getAssessment()) {
            case MULTIPLE_CHOICE:
                pollData.setAssessmentType(PollData.AssessmentType.MULTIPLE);
                break;
            case SINGLE_CHOICE:
            default:
                pollData.setAssessmentType(PollData.AssessmentType.SINGLE);
        }

        switch (pollModel.getState()) {
            case CLOSED:
                pollData.setState(PollData.State.CLOSED);
                break;
            case OPEN:
            default:
                pollData.setState(PollData.State.OPEN);
        }

        switch (pollModel.getDisplayType()) {
            case SUMMARY_MODE:
                pollData.setDisplayType(PollData.DisplayType.SUMMARY_MODE);
                break;
            case LIST_MODE:
            default:
                pollData.setDisplayType(PollData.DisplayType.LIST_MODE);
                break;
        }

        HashMap<String, Integer> votersPositions = new HashMap<>();
        List<PollVoteModel> voteModels = null;
        int votersCount = 0;
        if (isClosing || receivingIdentities != null) {
            // load a list of voters
            String[] voters = this.getVotedParticipants(pollModel.getId()).toArray(new String[0]);

            for (String s : voters) {
                pollData.addParticipant(s);
                votersPositions.put(s, votersCount);
                votersCount++;
            }

            voteModels = this.getPollVotes(pollModel.getId());
        }
        // if closing, add result!
        for (final PollChoiceModel c : choices) {
            PollDataChoice choice = new PollDataChoice(votersCount);
            choice.setId(c.getApiPollChoiceId());
            choice.setName(c.getName());
            choice.setOrder(c.getOrder());

            if ((isClosing || receivingIdentities != null) && voteModels != null) {
                voteModels.stream()
                    .filter(model -> model.getPollChoiceId() == c.getId())
                    .forEach(model -> {
                        int pos = votersPositions.get(model.getVotingIdentity());
                        if (pos >= 0) {
                            choice.addResult(pos, model.getChoice());
                        }
                    });

            }
            pollData.getChoiceList().add(choice);
        }

        try {
            messageReceiver.createAndSendPollSetupMessage(
                pollData,
                pollModel,
                abstractMessageModel,
                receivingIdentities,
                triggerSource
            );

            //set as open
            if (pollModel.getState() == PollModel.State.TEMPORARY) {
                pollModel.setState(PollModel.State.OPEN);
                pollModel.setModifiedAt(Instant.now());

                this.databaseService.getPollModelFactory().update(
                    pollModel
                );

            }

            result.success();
        } catch (ThreemaException e) {
            logger.error("create boxed poll failed", e);
            if (e instanceof MessageTooLongException) {
                throw new MessageTooLongException();
            }
        }

        return result;
    }

    @Override
    public LinkPollModel getLinkedPollModel(PollModel pollModel) throws NotAllowedException {
        if (pollModel == null) {
            return null;
        }

        LinkPollModel linkPollModel = this.getLinkModelFromCache(pollModel.getId());
        if (linkPollModel != null) {
            return linkPollModel;
        }

        GroupPollModel group = this.databaseService.getGroupPollModelFactory().getByPollId(pollModel.getId());

        if (group != null) {
            this.cache(group);
            return group;
        }

        IdentityPollModel identityPollModel = this.databaseService.getIdentityPollModelFactory().getByPollId(
            pollModel.getId()
        );
        if (identityPollModel != null) {
            this.cache(identityPollModel);
            return identityPollModel;
        }

        return null;
    }

    @Override
    public boolean remove(final PollModel pollModel) throws NotAllowedException {
        if (serviceManager == null) {
            logger.debug("Unable to delete poll, ServiceManager is not available");
            return false;
        }

        MessageService messageService = serviceManager.getMessageService();

        if (pollModel != null) {
            List<AbstractMessageModel> messageModels = messageService.getMessageForPoll(pollModel);

            //remove all votes
            this.databaseService.getPollVoteModelFactory().deleteByPollId(
                pollModel.getId());

            //remove choices
            this.databaseService.getPollChoiceModelFactory().deleteByPollId(
                pollModel.getId());

            //remove link
            this.databaseService.getGroupPollModelFactory().deleteByPollId(
                pollModel.getId());

            this.databaseService.getIdentityPollModelFactory().deleteByPollId(
                pollModel.getId());

            // remove poll
            this.databaseService.getPollModelFactory().delete(
                pollModel
            );

            // delete associated messages
            if (messageModels != null) {
                for (AbstractMessageModel m : messageModels) {
                    if (m != null) {
                        try {
                            logger.debug("Removing poll message {} of type {}", m.getApiMessageId() != null ? m.getApiMessageId() : m.getId(), m.getPollData().getType());
                            messageService.remove(m);
                        } catch (Exception e) {
                            logger.error("Unable to remove message", e);
                        }
                    }
                }
            }

            // remove poll from cache
            this.resetCache(pollModel);

            globalEventBuses.getPolls().emit(new PollEvent.PollRemoved(pollModel));
        }
        return true;
    }

    @Override
    public boolean remove(final MessageReceiver receiver) {
        try {
            for (PollModel pollModel : this.getPolls(new PollFilter() {
                @Override
                public MessageReceiver getReceiver() {
                    return receiver;
                }

                @Override
                public PollModel.State[] getStates() {
                    return null;
                }
            })) {
                if (!this.remove(pollModel)) {
                    return false;
                }
            }
        } catch (NotAllowedException x) {
            //do nothing more
            logger.error("Exception", x);
            return false;
        }

        return true;
    }

    @Override
    public boolean belongsToMe(Integer pollModelId, MessageReceiver messageReceiver) {
        PollModel pollModel = this.get(pollModelId);

        if (pollModel == null || messageReceiver == null) {
            return false;
        }

        switch (messageReceiver.getType()) {
            case MessageReceiver.Type_CONTACT:
            case MessageReceiver.Type_GROUP:
                LinkPollModel l;
                try {
                    l = this.getLinkedPollModel(pollModel);
                } catch (NotAllowedException e) {
                    return false;
                }
                if (l != null) {
                    if (messageReceiver.getType() == MessageReceiver.Type_GROUP && l.getType() == LinkPollModel.Type.GROUP) {
                        return ((GroupPollModel) l).getGroupId() == ((GroupMessageReceiver) messageReceiver).getGroup().getId();
                    } else if (messageReceiver.getType() == MessageReceiver.Type_CONTACT && l.getType() == LinkPollModel.Type.CONTACT) {
                        return areEqual(((IdentityPollModel) l).getIdentity(), ((ContactMessageReceiver) messageReceiver).getContact().getIdentity());
                    }
                }
        }

        return false;
    }

    @Override
    public PollVoteResult vote(
        Integer pollModelId,
        Map<Integer, Integer> voting,
        @NonNull TriggerSource triggerSource
    ) throws NotAllowedException {
        PollModel pollModel = this.get(pollModelId);

        if (pollModel == null || voting == null) {
            return new PollVoteResult(false);
        }

        List<PollChoiceModel> allChoices = this.getChoices(pollModel.getId());
        if (allChoices == null) {
            return new PollVoteResult(false);
        }

        LinkPollModel link = this.getLinkedPollModel(pollModel);
        MessageReceiver messageReceiver = this.getReceiver(link);

        if (messageReceiver == null) {
            return new PollVoteResult(false);
        }

        //prepare all messages and save local
        PollVote[] votes = new PollVote[allChoices.size()];
        int n = 0;
        for (final PollChoiceModel choiceModel : allChoices) {
            //change if other values implement
            final int voteValue;
            if (voting.containsKey(choiceModel.getId())) {
                voteValue = voting.get(choiceModel.getId());
            } else {
                voteValue = 0;
            }
            votes[n] = new PollVote(
                choiceModel.getApiPollChoiceId(),
                voteValue
            );
            n++;
        }

        try {
            //send
            messageReceiver.createAndSendPollVoteMessage(votes, pollModel, triggerSource);

            //and save
            this.databaseService.getPollVoteModelFactory().deleteByPollIdAndVotingIdentity(
                pollModel.getId(),
                this.userService.getIdentity()
            );

            for (PollChoiceModel choiceModel : allChoices) {
                PollVoteModel pollVoteModel = new PollVoteModel();
                pollVoteModel.setVotingIdentity(this.userService.getIdentity());
                pollVoteModel.setPollId(pollModel.getId());
                pollVoteModel.setPollChoiceId(choiceModel.getId());

                if (voting.containsKey(choiceModel.getId())) {
                    pollVoteModel.setChoice(voting.get(choiceModel.getId()));
                } else {
                    pollVoteModel.setChoice(0);
                }

                pollVoteModel.setModifiedAt(Instant.now());
                pollVoteModel.setCreatedAt(Instant.now());
                this.databaseService.getPollVoteModelFactory().create(
                    pollVoteModel
                );
            }
        } catch (ThreemaException e) {
            logger.error("create boxed poll failed", e);
            return new PollVoteResult(false);
        }

        globalEventBuses.getPolls().emit(new PollEvent.PollSelfVoted(pollModel));

        return new PollVoteResult(true);
    }

    @Override
    public PollVoteResult vote(@NonNull final PollVoteInterface pollVoteMessage) throws NotAllowedException {
        final PollId pollId = pollVoteMessage.getPollId();

        if (pollId == null) {
            logger.warn("Invalid vote message, poll id is null.");
            return new PollVoteResult(false);
        }

        final PollModel pollModel = this.get(pollId.toString(), pollVoteMessage.getPollCreatorIdentity());

        // Invalid poll model
        if (pollModel == null) {
            logger.warn("No poll found for poll id");
            return new PollVoteResult(false);
        }

        if (hasDuplicatesBy(pollVoteMessage.getVotes(), PollVote::getId)) {
            logger.warn("Invalid vote message: duplicate vote ids");
            return new PollVoteResult(false);
        }

        if (pollModel.getAssessment() == PollModel.Assessment.SINGLE_CHOICE) {
            var selectedChoices = pollVoteMessage.getVotes()
                .stream()
                .filter(PollVote::isSelected)
                .count();
            if (selectedChoices > 1) {
                logger.warn("Invalid vote message: multiple choices selected in single-choice poll");
                return new PollVoteResult(false);
            }
        }

        final String fromIdentity = ((AbstractMessage) pollVoteMessage).getFromIdentity();

        if (pollModel.getType() == PollModel.Type.RESULT_ON_CLOSE) {
            final String pollCreatorIdentity = pollModel.getCreatorIdentity();
            final String myIdentity = this.userService.getIdentity();
            // When a vote from someone else is received in a RESULT_ON_CLOSE poll, where we are not
            // the creator, this should not happen and the message must be ignored.
            // If a vote is received from ourselves in such a case this is a reflected vote that must
            // be processed.
            if (!areEqual(pollCreatorIdentity, myIdentity)
                && !areEqual(fromIdentity, myIdentity)) {
                logger.warn("Intermediate results are not shown for this poll. Ignore message.");
                // Return true to ack the message
                return new PollVoteResult(true);
            }
        }

        // If the poll is closed, ignore any votes
        if (pollModel.getState() == PollModel.State.CLOSED) {
            logger.error("this is a closed poll, ignore this message");
            return new PollVoteResult(true);
        }

        // Load existing votes of user
        List<PollVoteModel> existingVotes = this.getVotes(pollModel.getId(), fromIdentity);
        final boolean firstVote = existingVotes.isEmpty();

        List<PollVoteModel> savingVotes = new ArrayList<>();
        List<PollChoiceModel> choices = this.getChoices(pollModel.getId());

        for (final PollVote apiVoteModel : pollVoteMessage.getVotes()) {
            // Check if the choice correct
            final PollChoiceModel pollChoiceModel = choices.stream()
                .filter(model -> model.getApiPollChoiceId() == apiVoteModel.getId())
                .findFirst()
                .orElse(null);

            if (pollChoiceModel != null) {
                // Cool, correct choice
                PollVoteModel pollVoteModel = existingVotes.stream()
                    .filter(model -> model.getPollChoiceId() == pollChoiceModel.getId())
                    .findFirst()
                    .orElse(null);

                if (pollVoteModel == null) {
                    // Ok, a new vote
                    pollVoteModel = new PollVoteModel();
                    pollVoteModel.setPollId(pollModel.getId());
                    pollVoteModel.setPollChoiceId(pollChoiceModel.getId());
                    pollVoteModel.setVotingIdentity(fromIdentity);
                    pollVoteModel.setCreatedAt(Instant.now());
                } else {
                    // Remove from existing votes
                    existingVotes.remove(pollVoteModel);
                }

                if (
                    // Is a new vote...
                    pollVoteModel.getId() <= 0
                        // ... or a modified
                        || pollVoteModel.getChoice() != apiVoteModel.getValue()) {

                    pollVoteModel.setChoice(apiVoteModel.getValue());
                    pollVoteModel.setModifiedAt(Instant.now());
                    savingVotes.add(pollVoteModel);
                }
            }
        }

        // Remove votes
        boolean hasModifications = false;

        if (existingVotes != null && !existingVotes.isEmpty()) {
            int[] ids = new int[existingVotes.size()];
            for (int n = 0; n < ids.length; n++) {
                ids[n] = existingVotes.get(n).getId();
            }

            this.databaseService.getPollVoteModelFactory().deleteByIds(ids);

            hasModifications = true;
        }

        for (PollVoteModel pollVoteModel : savingVotes) {
            this.databaseService.getPollVoteModelFactory().createOrUpdate(
                pollVoteModel
            );
            hasModifications = true;
        }

        if (hasModifications) {
            if (fromIdentity.equals(userService.getIdentity())) {
                globalEventBuses.getPolls().emit(new PollEvent.PollSelfVoted(pollModel));
            } else {
                globalEventBuses.getPolls().emit(PollEvent.PollVoted.javaCreate(pollModel, fromIdentity, firstVote));
            }
        }
        return new PollVoteResult(true);
    }


    private GroupModelOld getGroupModel(LinkPollModel link) {
        if (link.getType() != LinkPollModel.Type.GROUP) {
            return null;
        }

        int groupId = ((GroupPollModel) link).getGroupId();
        return this.groupService.getById(groupId);
    }


    private ContactModel getContactModel(LinkPollModel link) {
        if (link.getType() != LinkPollModel.Type.CONTACT) {
            return null;
        }

        String identity = ((IdentityPollModel) link).getIdentity();
        return this.contactService.getByIdentity(identity);

    }

    @Override
    public MessageReceiver getReceiver(PollModel pollModel) {
        try {
            LinkPollModel link = this.getLinkedPollModel(pollModel);
            return this.getReceiver(link);
        } catch (NotAllowedException e) {
            logger.error("Exception", e);
            return null;
        }
    }

    @Override
    public PollMatrixData getMatrixData(int pollModelId) {
        try {
            PollModel pollModel = this.get(pollModelId);

            // ok, poll not found
            if (pollModel == null) {
                throw new ThreemaException("invalid poll");
            }

            PollMatrixService matrixService = new PollMatrixServiceImpl(pollModel);

            String[] participants = this.getParticipants(pollModelId);

            if (participants.length > 0) {
                for (String identity : participants) {
                    matrixService.createParticipant(identity);
                }

                for (PollChoiceModel choice : this.getChoices(pollModelId)) {
                    matrixService.createChoice(choice);
                }

                for (PollVoteModel pollVoteModel : this.getPollVotes(pollModelId)) {
                    matrixService.addVote(pollVoteModel);
                }

                return matrixService.finish();
            }
        } catch (ThreemaException x) {
            logger.error("Exception", x);
        }
        return null;
    }

    private MessageReceiver getReceiver(LinkPollModel link) {
        if (link != null) {
            switch (link.getType()) {
                case GROUP:
                    GroupModelOld groupModel = this.getGroupModel(link);
                    return this.groupService.createReceiver(groupModel);
                case CONTACT:
                    ContactModel contactModel = this.getContactModel(link);
                    return this.contactService.createReceiver(contactModel);
            }
        }
        return null;
    }

    private int getCalculatedVotingCount(PollChoiceModel choiceModel) {
        return (int) this.databaseService.getPollVoteModelFactory().countByPollChoiceIdAndChoice(
            choiceModel.getId(),
            1);
    }

    private PollChoiceModel getChoiceByApiId(PollModel pollModel, int choiceId) {
        return this.databaseService.getPollChoiceModelFactory().getByPollIdAndApiChoiceId(
            pollModel.getId(),
            choiceId
        );
    }

    /**
     * Link a poll with a contact
     *
     * @return success
     */
    private boolean link(ContactModel contactModel, PollModel pollModel) {
        IdentityPollModelFactory identityPollModelFactory = this.databaseService.getIdentityPollModelFactory();
        if (identityPollModelFactory.getByIdentityAndPollId(
            contactModel.getIdentity(),
            pollModel.getId()
        ) != null) {
            //already linked
            return true;
        }

        IdentityPollModel m = new IdentityPollModel();
        m.setPollId(pollModel.getId());
        m.setIdentity(contactModel.getIdentity());
        identityPollModelFactory.create(
            m);

        this.cache(m);

        return true;
    }

    /**
     * Link a poll with a group
     *
     * @return success
     */
    private boolean link(GroupModelOld groupModel, PollModel pollModel) {
        GroupPollModelFactory groupPollModelFactory = this.databaseService.getGroupPollModelFactory();
        if (groupPollModelFactory.getByGroupIdAndPollId(
            groupModel.getId(),
            pollModel.getId()
        ) != null) {
            //already linked
            return true;
        }

        GroupPollModel m = new GroupPollModel();
        m.setPollId(pollModel.getId());
        m.setGroupId(groupModel.getId());
        groupPollModelFactory.create(
            m);

        this.cache(m);
        return true;
    }

    private void checkAccess() throws NotAllowedException {
        if (!this.userService.hasIdentity()) {
            throw new NotAllowedException();
        }
    }

    private void cache(List<PollModel> pollModels) {
        for (PollModel m : pollModels) {
            this.cache(m);
        }
    }

    private void cache(PollModel pollModel) {
        if (pollModel != null) {
            synchronized (this.pollModelCache) {
                this.pollModelCache.put(pollModel.getId(), pollModel);
            }
        }
    }

    private void cache(LinkPollModel linkPollModel) {
        if (linkPollModel != null) {
            synchronized (this.linkPollModelCache) {
                this.linkPollModelCache.put(linkPollModel.getPollId(), linkPollModel);
            }
        }
    }

    private void resetCache(PollModel pollModel) {
        if (pollModel != null) {
            synchronized (this.pollModelCache) {
                this.pollModelCache.remove(pollModel.getId());
            }
        }
    }

    @Nullable
    private PollModel getFromCache(int id) {
        synchronized (this.pollModelCache) {
            if (this.pollModelCache.indexOfKey(id) >= 0) {
                return this.pollModelCache.get(id);
            }
        }

        return null;
    }

    private LinkPollModel getLinkModelFromCache(int pollId) {
        synchronized (this.linkPollModelCache) {
            if (this.linkPollModelCache.indexOfKey(pollId) >= 0) {
                return this.linkPollModelCache.get(pollId);
            }
        }
        return null;
    }

    private PollModel getFromCache(final String apiId, final String creator) {
        synchronized (this.pollModelCache) {
            return select(this.pollModelCache, type ->
                areEqual(type.getApiPollId(), apiId) && areEqual(type.getCreatorIdentity(), creator)
            );
        }
    }

    private static <T> T select(SparseArray<T> target, Predicate<T> predicate) {
        for (int n = 0; n < target.size(); n++) {
            int key = target.keyAt(n);
            T object = target.get(key);
            if (object != null && predicate.test(object)) {
                return object;
            }
        }
        return null;
    }
}
