package ch.threema.domain.protocol.csp.messages.file;

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

    /**
     * Encrypt a file for a group.
     */
    @Test
    public void groupTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
        //create a new file message
        final String myIdentity = "TESTTEST";
        final String toIdentity = "ABCDEFGH";

        byte[] blobIdFile = new byte[ProtocolDefines.BLOB_ID_LEN];
        byte[] blobIdThumbnail = new byte[ProtocolDefines.BLOB_ID_LEN];
        byte[] key = new byte[ProtocolDefines.BLOB_KEY_LEN];

        GroupId groupId = new GroupId(new byte[ProtocolDefines.GROUP_ID_LEN]);
        String groupCreator = myIdentity;

        GroupFileMessage groupFileMessage = new GroupFileMessage();
        groupFileMessage.setFromIdentity(toIdentity);
        groupFileMessage.setToIdentity(myIdentity);
        groupFileMessage.setApiGroupId(groupId);
        groupFileMessage.setGroupCreator(groupCreator);
        FileData data = new FileData();
        data
            .setFileBlobId(blobIdFile)
            .setThumbnailBlobId(blobIdThumbnail)
            .setEncryptionKey(key)
            .setMimeType("image/jpg")
            .setFileName("therme.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_DEFAULT);
        groupFileMessage.setFileData(data);

        ContactStore contactStore = TestHelpers.getNoopContactStore();
        IdentityStore identityStore = TestHelpers.getNoopIdentityStore();
        MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

        NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();

        MessageBox boxmsg = messageCoder.encode(groupFileMessage, nonceFactory.nextNonce(NonceScope.CSP));
        Assertions.assertNotNull(boxmsg, "BoxMessage failed");

        //now decode again
        AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg);
        Assertions.assertNotNull(decodedBoxMessage, "decodedBox failed");
        Assertions.assertInstanceOf(GroupFileMessage.class, decodedBoxMessage);

        GroupFileMessage groupFileMessageDecoded = (GroupFileMessage) decodedBoxMessage;
        FileData fileData = groupFileMessageDecoded.getFileData();
        Assertions.assertNotNull(fileData);

        Assertions.assertArrayEquals(blobIdFile, fileData.getFileBlobId());
        Assertions.assertArrayEquals(blobIdThumbnail, fileData.getThumbnailBlobId());
        Assertions.assertArrayEquals(key, fileData.getEncryptionKey());
        Assertions.assertEquals("image/jpg", fileData.getMimeType());
        Assertions.assertEquals("therme.jpg", fileData.getFileName());
        Assertions.assertEquals(123, fileData.getFileSize());
        Assertions.assertEquals(FileData.RENDERING_DEFAULT, fileData.getRenderingType());
    }

    @Test
    public void identityTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
        //create a new file message
        final String myIdentity = "TESTTEST";
        final String toIdentity = "ABCDEFGH";

        byte[] blobIdFile = new byte[ProtocolDefines.BLOB_ID_LEN];
        byte[] blobIdThumbnail = new byte[ProtocolDefines.BLOB_ID_LEN];
        byte[] key = new byte[ProtocolDefines.BLOB_KEY_LEN];

        FileMessage fileMessage = new FileMessage();
        fileMessage.setFromIdentity(toIdentity);
        fileMessage.setToIdentity(myIdentity);
        FileData data = new FileData();
        data
            .setFileBlobId(blobIdFile)
            .setThumbnailBlobId(blobIdThumbnail)
            .setEncryptionKey(key)
            .setMimeType("image/jpg")
            .setFileName("therme.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_MEDIA);
        fileMessage.setFileData(data);

        ContactStore contactStore = TestHelpers.getNoopContactStore();
        IdentityStore identityStore = TestHelpers.getNoopIdentityStore();
        MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

        NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();

        MessageBox boxmsg = messageCoder.encode(fileMessage, nonceFactory.nextNonce(NonceScope.CSP));
        Assertions.assertNotNull(boxmsg, "BoxMessage failed");

        //now decode again
        AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg);
        Assertions.assertNotNull(decodedBoxMessage, "decodedBox failed");
        Assertions.assertInstanceOf(FileMessage.class, decodedBoxMessage);

        FileMessage fileMessageDecoded = (FileMessage) decodedBoxMessage;
        FileData fileData = fileMessageDecoded.getFileData();
        Assertions.assertNotNull(fileData);

        Assertions.assertArrayEquals(blobIdFile, fileData.getFileBlobId());
        Assertions.assertArrayEquals(blobIdThumbnail, fileData.getThumbnailBlobId());
        Assertions.assertArrayEquals(key, fileData.getEncryptionKey());
        Assertions.assertEquals("image/jpg", fileData.getMimeType());
        Assertions.assertEquals("therme.jpg", fileData.getFileName());
        Assertions.assertEquals(123, fileData.getFileSize());
        Assertions.assertEquals(FileData.RENDERING_MEDIA, fileData.getRenderingType());
    }
}
