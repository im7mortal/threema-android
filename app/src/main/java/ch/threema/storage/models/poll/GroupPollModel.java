package ch.threema.storage.models.poll;

public class GroupPollModel implements LinkPollModel {
    public static final String TABLE = "group_ballot";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_GROUP_ID = "groupId";
    public static final String COLUMN_POLL_ID = "ballotId";

    private int id;
    private int groupId;
    private int pollId;

    public int getId() {
        return this.id;
    }

    public GroupPollModel setId(int id) {
        this.id = id;
        return this;
    }

    public int getGroupId() {
        return groupId;
    }

    public GroupPollModel setGroupId(int groupId) {
        this.groupId = groupId;
        return this;
    }

    public int getPollId() {
        return pollId;
    }

    @Override
    public Type getType() {
        return Type.GROUP;
    }

    public GroupPollModel setPollId(int pollId) {
        this.pollId = pollId;
        return this;
    }
}
