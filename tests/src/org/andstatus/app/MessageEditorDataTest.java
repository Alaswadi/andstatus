package org.andstatus.app;

import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.MessageEditorData;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyQuery;

public class MessageEditorDataTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }
    
    public void testMessageEditorDataConversation() {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        long entryMsgId = MyQuery.oidToId(OidEnum.MSG_OID, MyContextHolder.get()
                .persistentOrigins()
                .fromName(TestSuite.CONVERSATION_ORIGIN_NAME).getId(),
                TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        long entryUserId = MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                TestSuite.CONVERSATION_ENTRY_USER_OID);
        long memberUserId = MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                TestSuite.CONVERSATION_MEMBER_USER_OID);
        assertData(ma, entryMsgId, entryUserId, 0, memberUserId, false);
        assertData(ma, entryMsgId, entryUserId, 0, memberUserId, true);
        assertData(ma,          0,           0, memberUserId, 0, false);
        assertData(ma,          0,           0, memberUserId, 0, true);
    }

    private void assertData(MyAccount ma, long inReplyToMsgId, long inReplyToUserId, long recipientId,
            long memberUserId, boolean replyAll) {
        MessageEditorData data = new MessageEditorData(ma)
                .setMediaUri(Uri.parse("http://example.com/" + TestSuite.TESTRUN_UID + "/some.png"))
                .setInReplyToId(inReplyToMsgId)
                .setRecipientId(recipientId)
                .setReplyAll(replyAll)
                .setMessageText("Some text " + TestSuite.TESTRUN_UID);
        assertFalse(data.toString(), data.messageText.contains("@"));
        data.addMentionsToText();
        assertEquals(recipientId, data.recipientId);
        assertMentionedUser(data, inReplyToUserId, true);
        assertMentionedUser(data, memberUserId, replyAll);
    }

    private void assertMentionedUser(MessageEditorData data, long mentionedUserId,
            boolean isMentioned_in) {
        if (mentionedUserId == 0) {
            return;
        }
        String expectedName = MyQuery.userIdToStringColumnValue(
                data.ma.getOrigin().isMentionAsWebFingerId() ? MyDatabase.User.WEBFINGER_ID
                        : MyDatabase.User.USERNAME, mentionedUserId);
        assertTrue(!TextUtils.isEmpty(expectedName));
        boolean isMentioned = data.messageText.contains("@" + expectedName);
        assertEquals(data.toString() + "; expected name:" + expectedName, isMentioned_in, isMentioned);
    }

}
