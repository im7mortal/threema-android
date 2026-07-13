package ch.threema.app.utils;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class IdUtilTest {

    @Test
    public void getTempUniqueId() {
        String identity1 = "AAAAAAAA";
        String identity2 = "BBBBBBBB";
        String identity3 = "CCCCCCCC";
        assertEquals(1, IdUtil.getContactTempId(identity1));
        assertEquals(1, IdUtil.getContactTempId(identity1));
        assertEquals(2, IdUtil.getContactTempId(identity3));
        assertEquals(1, IdUtil.getContactTempId(identity1));
        assertEquals(2, IdUtil.getContactTempId(identity3));
        assertEquals(3, IdUtil.getContactTempId(identity2));
    }
}
