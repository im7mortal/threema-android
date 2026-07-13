package ch.threema.domain.protocol.csp.messages.poll;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PollDataChoiceBuilderTest {
    @Test
    public void buildValidNoVotes() {
        final PollDataChoice choice = new PollDataChoiceBuilder()
            .setId(123)
            .setDescription("This is a choice")
            .setSortKey(1)
            .build();
        Assertions.assertEquals(123, choice.getId());
        Assertions.assertEquals("This is a choice", choice.getName());
        Assertions.assertEquals(1, choice.getOrder());
        Assertions.assertNull(choice.getResult(0));
    }

    @Test
    public void buildValidWithVotes() {
        final PollDataChoice choice = new PollDataChoiceBuilder()
            .setId(123)
            .setDescription("This is a choice")
            .setSortKey(1)
            .addVote(3)
            .addVote(1)
            .addVote(2)
            .build();
        Assertions.assertEquals(Integer.valueOf(3), choice.getResult(0));
        Assertions.assertEquals(Integer.valueOf(1), choice.getResult(1));
        Assertions.assertEquals(Integer.valueOf(2), choice.getResult(2));
    }

    @Test
    public void buildInvalidNoId() {
        final PollDataChoiceBuilder builder = new PollDataChoiceBuilder()
            .setDescription("This is a choice")
            .setSortKey(1);
        try {
            builder.build();
            Assertions.fail("BuilderException not thrown");
        } catch (IllegalArgumentException e) {
            Assertions.assertEquals("Cannot build PollDataChoice: id is null", e.getMessage());
        }
    }

    @Test
    public void buildInvalidNoDescription() {
        final PollDataChoiceBuilder builder = new PollDataChoiceBuilder()
            .setId(987)
            .setSortKey(1);
        try {
            builder.build();
            Assertions.fail("BuilderException not thrown");
        } catch (IllegalArgumentException e) {
            Assertions.assertEquals("Cannot build PollDataChoice: description is null", e.getMessage());
        }
    }

    @Test
    public void buildInvalidNoSortKey() {
        final PollDataChoiceBuilder builder = new PollDataChoiceBuilder()
            .setId(987)
            .setDescription("This is a choice");
        try {
            builder.build();
            Assertions.fail("BuilderException not thrown");
        } catch (IllegalArgumentException e) {
            Assertions.assertEquals("Cannot build PollDataChoice: sortKey is null", e.getMessage());
        }
    }
}
