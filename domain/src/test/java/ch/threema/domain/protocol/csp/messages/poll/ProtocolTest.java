package ch.threema.domain.protocol.csp.messages.poll;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceScope;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.domain.testhelpers.TestHelpers;

public class ProtocolTest {

    @Test
    public void groupTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
        //create a new poll
        final String myIdentity = "TESTTEST";
        final String toIdentity = "ABCDEFGH";

        PollId pollId = new PollId(new byte[ProtocolDefines.POLL_ID_LEN]);
        String pollCreator = toIdentity;

        GroupId groupId = new GroupId(new byte[ProtocolDefines.GROUP_ID_LEN]);
        String groupCreator = pollCreator;

        GroupPollSetupMessage b = new GroupPollSetupMessage();
        b.setFromIdentity(pollCreator);
        b.setToIdentity(myIdentity);
        b.setApiGroupId(groupId);
        b.setGroupCreator(groupCreator);
        b.setPollId(pollId);
        b.setPollCreatorIdentity(pollCreator);
        PollData data = new PollData();
        data.setDescription("Test Poll");
        data.setType(PollData.Type.RESULT_ON_CLOSE);
        data.setAssessmentType(PollData.AssessmentType.SINGLE);
        data.setState(PollData.State.OPEN);
        data.setDisplayType(PollData.DisplayType.LIST_MODE);


        for (int n = 0; n < 10; n++) {
            PollDataChoice c = new PollDataChoice(2);
            c.setId(n + 1);
            c.setName("Choice " + (n + 1));
            c.setOrder(n);
            c.addResult(0, 1).addResult(1, 0);
            c.setTotalVotes(2);
            data.getChoiceList().add(c);
        }
        b.setPollData(data);

        ContactStore contactStore = TestHelpers.getNoopContactStore();
        IdentityStore identityStore = TestHelpers.getNoopIdentityStore();
        NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();
        MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

        MessageBox boxmsg = messageCoder.encode(b, nonceFactory.nextNonce(NonceScope.CSP));
        Assertions.assertNotNull(boxmsg, "BoxMessage failed");

        //now decode again
        AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg);
        Assertions.assertNotNull(decodedBoxMessage, "decodedBox failed");
        Assertions.assertInstanceOf(GroupPollSetupMessage.class, decodedBoxMessage);

        GroupPollSetupMessage db = (GroupPollSetupMessage) decodedBoxMessage;

        PollData d = db.getPollData();
        Assertions.assertNotNull(d);

        Assertions.assertEquals(PollData.State.OPEN, d.getState());
        Assertions.assertEquals(PollData.AssessmentType.SINGLE, d.getAssessmentType());
        Assertions.assertEquals(PollData.Type.RESULT_ON_CLOSE, d.getType());
        Assertions.assertEquals(10, b.getPollData().getChoiceList().size());
        Assertions.assertEquals("Choice 7", b.getPollData().getChoiceList().get(6).getName());
        Assertions.assertEquals(1, (int) b.getPollData().getChoiceList().get(2).getResult(0));
        Assertions.assertEquals(0, (int) b.getPollData().getChoiceList().get(2).getResult(1));
    }


    @Test
    public void identityTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
        //create a new poll
        final String myIdentity = "TESTTEST";
        final String toIdentity = "ABCDEFGH";

        PollId pollId = new PollId(new byte[ProtocolDefines.POLL_ID_LEN]);
        String pollCreator = toIdentity;

        PollSetupMessage pollSetupMessage = new PollSetupMessage();
        pollSetupMessage.setFromIdentity(pollCreator);
        pollSetupMessage.setToIdentity(myIdentity);
        pollSetupMessage.setPollId(pollId);
        pollSetupMessage.setPollCreatorIdentity(pollCreator);
        PollData data = new PollData();
        data.setDescription("Test Poll");
        data.setType(PollData.Type.RESULT_ON_CLOSE);
        data.setAssessmentType(PollData.AssessmentType.SINGLE);
        data.setState(PollData.State.OPEN);


        for (int n = 0; n < 10; n++) {
            PollDataChoice c = new PollDataChoice(2);
            c.setId(n + 1);
            c.setName("Choice " + (n + 1));
            c.setOrder(n);
            c.addResult(0, 1).addResult(1, 0);
            data.getChoiceList().add(c);
        }
        pollSetupMessage.setPollData(data);

        ContactStore contactStore = TestHelpers.getNoopContactStore();
        IdentityStore identityStore = TestHelpers.getNoopIdentityStore();
        MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

        NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();

        MessageBox boxmsg = messageCoder.encode(pollSetupMessage, nonceFactory.nextNonce(NonceScope.CSP));
        Assertions.assertNotNull(boxmsg, "BoxMessage failed");

        //now decode again
        AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg);
        Assertions.assertNotNull(decodedBoxMessage, "decodedBox failed");
        Assertions.assertInstanceOf(PollSetupMessage.class, decodedBoxMessage);

        PollSetupMessage db = (PollSetupMessage) decodedBoxMessage;

        PollData pollData = db.getPollData();
        Assertions.assertNotNull(pollData);

        Assertions.assertEquals(PollData.State.OPEN, pollData.getState());
        Assertions.assertEquals(PollData.AssessmentType.SINGLE, pollData.getAssessmentType());
        Assertions.assertEquals(PollData.Type.RESULT_ON_CLOSE, pollData.getType());
        Assertions.assertEquals(10, pollSetupMessage.getPollData().getChoiceList().size());
        Assertions.assertEquals("Choice 7", pollSetupMessage.getPollData().getChoiceList().get(6).getName());
        Assertions.assertEquals(1, (int) pollSetupMessage.getPollData().getChoiceList().get(2).getResult(0));
        Assertions.assertEquals(0, (int) pollSetupMessage.getPollData().getChoiceList().get(2).getResult(1));
    }
}
