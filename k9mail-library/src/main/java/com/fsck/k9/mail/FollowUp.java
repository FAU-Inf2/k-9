package com.fsck.k9.mail;
import java.util.Date;

public class FollowUp {

    private int id;
    private String title;
    private Date remindTime;
    private Message message;
    private long folderId;
    private String uid;
    private String messageId;

    public FollowUp() {
    }

    public FollowUp(String title, Date remindTime) {
        this(title, remindTime, null, -1, null, null);
    }

    public FollowUp(String title, Date remindTime, Message reference) {
        this(title, remindTime, reference, -1, null, null);
    }

    public FollowUp(String title, Date remindTime, long folderId) {
        this(title, remindTime, null, folderId, null, null);
    }

    public FollowUp(String title, Date remindTime, Message reference, long folderId) {
        this(title, remindTime, reference, folderId, null, null);
    }

    public FollowUp(String title, Date remindTime, Message reference, long folderId, String uid,
                    String messageId) {
        setTitle(title);
        setRemindTime(remindTime);
        setReference(reference);
        setFolderId(folderId);
        if(uid == null && reference != null)
            uid = reference.getUid();
        if(messageId == null && reference != null)
            try {
                messageId = reference.getMessageId();
            }catch (Exception e) {
            }
        setUid(uid);
        setMessageId(messageId);
    }

    public Date getRemindTime() {
        return remindTime;
    }

    public void setRemindTime(Date remindTime) {
        this.remindTime = remindTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Message getReference() {
        return message;
    }

    public void setReference(Message reference) {
        this.message = reference;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getFolderId() {
        return folderId;
    }

    public void setFolderId(long folderId) {
        this.folderId = folderId;
    }

    public String getUid() {
        return this.uid;
    }
    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getMessageId() {
        return this.messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
