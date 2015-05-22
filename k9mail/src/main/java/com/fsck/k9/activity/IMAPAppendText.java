package com.fsck.k9.activity;

import android.os.AsyncTask;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.controller.MessagingListener;
import com.fsck.k9.controller.UnavailableAccountException;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.fsck.k9.mailstore.UnavailableStorageException;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class IMAPAppendText extends K9Activity {
/* Use IMAP-command "append" to upload a text to the server.*/
/* TODO:
* 1. get_current_uid
* [1a get newest version --> check if local uid is same as uid on server (caller has to check; not in this class)]
* [1b download newest version (here) and update content locally (caller)]
* 2. upload new version with new uid
* 3. delete old one from server.
* */

    private Account mAccount;
    private MimeMessage mimeMessage;

    public static final long INVALID_MESSAGE_ID = -1;
    private static final long INVALID_DRAFT_ID = INVALID_MESSAGE_ID;
    private static final String PENDING_COMMAND_APPEND = "com.fsck.k9.MessagingController.append";

    private Set<MessagingListener> mListeners = new CopyOnWriteArraySet<MessagingListener>();
    private BlockingQueue<Command> mCommands = new PriorityBlockingQueue<Command>();

    /**
     * The database ID of this message's draft. This is used when saving drafts so the message in
     * the database is updated instead of being created anew. This property is INVALID_DRAFT_ID
     * until the first save.
     * TODO: Look at processDraftMessage(LocalMessage message)
     */
    private long mDraftId = INVALID_DRAFT_ID;

    public IMAPAppendText(Account account) {
        this.mAccount = account;
    }

    public String get_current_uid(){
        //TODO
        return null;
    }

    public MimeMessage get_current_content() {
        //TODO
        return null;
    }

    public boolean append_new_content(String new_content) throws MessagingException {
        /*same like append_new_mime_message() but with String containing content; sets new uid */
        MimeMessage mimeMessage = new MimeMessage();
        //TODO: create uid (maybe timestamp + magic string?)
        long unixTime = System.currentTimeMillis() / 1000L;
        String uid = "ouruid"; //TODO: edit
        uid += String.valueOf(unixTime);

        mimeMessage.setUid(uid);
        mimeMessage.setBody(new TextBody(new_content + " -- uid is " + uid));

        append_new_mime_message(mimeMessage);

        return true;
    }

    public boolean append_new_mime_message(MimeMessage mimeMessage) {
        /*same like append_new_content() but with full MimeMessage containing content and new uid. */
        this.mimeMessage = mimeMessage;
        saveMessage();

        return true;
    }

    public void set_new_folder(String folder) {
        //sets new folder in which the content has to be stored
        //TODO
    }


    private void saveMessage() {
        new SaveMessageTask().execute();
    }

    /* see MessageCompose */
    private class SaveMessageTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            final MessagingController messagingController = MessagingController.getInstance(getApplication());
            Message draftMessage = messagingController.saveDraft(mAccount, mimeMessage, mDraftId);
            mDraftId = messagingController.getId(draftMessage);

            return null;
        }
    }

    /**
     *from MessagingController
     *
     * Save a draft message.
     * @param account Account we are saving for.
     * @param message Message to save.
     * @return Message representing the entry in the local store.
     */
    public Message saveDraft(final Account account, final Message message, long existingDraftId) {
        Message localMessage = null;
        try {
            LocalStore localStore = account.getLocalStore();
            LocalFolder localFolder = localStore.getFolder(account.getDraftsFolderName());
            localFolder.open(Folder.OPEN_MODE_RW);

            if (existingDraftId != INVALID_MESSAGE_ID) {
                String uid = localFolder.getMessageUidById(existingDraftId);
                message.setUid(uid);
            }

            // Save the message to the store.
            localFolder.appendMessages(Collections.singletonList(message));
            // Fetch the message back from the store.  This is the Message that's returned to the caller.
            localMessage = localFolder.getMessage(message.getUid());
            localMessage.setFlag(Flag.X_DOWNLOADED_FULL, true);

            LocalStore.PendingCommand command = new LocalStore.PendingCommand();
            command.command = PENDING_COMMAND_APPEND;
            command.arguments = new String[] {
                    localFolder.getName(),
                    localMessage.getUid()
            };
            queuePendingCommand(account, command);
            processPendingCommands(account);

        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "Unable to save message as draft.", e);
            //addErrorMessage(account, null, e);
        }
        return localMessage;
    }

    /* from MessagingController */
    private void queuePendingCommand(Account account, LocalStore.PendingCommand command) {
        try {
            LocalStore localStore = account.getLocalStore();
            localStore.addPendingCommand(command);
        } catch (Exception e) {
            //addErrorMessage(account, null, e);

            throw new RuntimeException("Unable to enqueue pending command", e);
        }
    }

    /* from MessagingController */
    private void processPendingCommands(final Account account) {
        putBackground("processPendingCommands", null, new Runnable() {
            @Override
            public void run() {
                try {
                    processPendingCommandsSynchronous(account);
                } catch (UnavailableStorageException e) {
                    Log.i(K9.LOG_TAG, "Failed to process pending command because storage is not available - trying again later.");
                    throw new UnavailableAccountException(e);
                } catch (MessagingException me) {
                    Log.e(K9.LOG_TAG, "processPendingCommands", me);

                    //addErrorMessage(account, null, me);

                    /*
                     * Ignore any exceptions from the commands. Commands will be processed
                     * on the next round.
                     */
                }
            }
        });
    }

    /* from MessagingController */
    private void processPendingCommandsSynchronous(Account account) throws MessagingException {
        LocalStore localStore = account.getLocalStore();
        List<LocalStore.PendingCommand> commands = localStore.getPendingCommands();

        int progress = 0;
        int todo = commands.size();
        if (todo == 0) {
            return;
        }

        for (MessagingListener l : getListeners()) {
            l.pendingCommandsProcessing(account);
            l.synchronizeMailboxProgress(account, null, progress, todo);
        }

        LocalStore.PendingCommand processingCommand = null;
        try {
            for (LocalStore.PendingCommand command : commands) {
                processingCommand = command;
                if (K9.DEBUG)
                    Log.d(K9.LOG_TAG, "Processing pending command '" + command + "'");

                String[] components = command.command.split("\\.");
                String commandTitle = components[components.length - 1];
                for (MessagingListener l : getListeners()) {
                    l.pendingCommandStarted(account, commandTitle);
                }
                /*
                 * We specifically do not catch any exceptions here. If a command fails it is
                 * most likely due to a server or IO error and it must be retried before any
                 * other command processes. This maintains the order of the commands.
                 */
                try {
                    if (PENDING_COMMAND_APPEND.equals(command.command)) {
                        processPendingAppend(command, account);
                    }
                    localStore.removePendingCommand(command);
                    if (K9.DEBUG)
                        Log.d(K9.LOG_TAG, "Done processing pending command '" + command + "'");
                } catch (MessagingException me) {
                    if (me.isPermanentFailure()) {
                        Log.e(K9.LOG_TAG, "Failure of command '" + command + "' was permanent, removing command from queue");
                        localStore.removePendingCommand(processingCommand);
                    } else {
                        throw me;
                    }
                } finally {
                    progress++;
                    for (MessagingListener l : getListeners()) {
                        l.synchronizeMailboxProgress(account, null, progress, todo);
                        l.pendingCommandCompleted(account, commandTitle);
                    }
                }
            }
        } catch (MessagingException me) {

            Log.e(K9.LOG_TAG, "Could not process command '" + processingCommand + "'", me);
            throw me;
        } finally {
            for (MessagingListener l : getListeners()) {
                l.pendingCommandsFinished(account);
            }
        }
    }

    /**
     * from MessagingController
     *
     * Process a pending append message command. This command uploads a local message to the
     * server, first checking to be sure that the server message is not newer than
     * the local message. Once the local message is successfully processed it is deleted so
     * that the server message will be synchronized down without an additional copy being
     * created.
     * TODO update the local message UID instead of deleteing it
     *
     * @param command arguments = (String folder, String uid)
     * @param account
     * @throws MessagingException
     */
    private void processPendingAppend(LocalStore.PendingCommand command, Account account)
            throws MessagingException {
        Folder remoteFolder = null;
        LocalFolder localFolder = null;
        try {

            String folder = command.arguments[0];
            String uid = command.arguments[1];

            if (account.getErrorFolderName().equals(folder)) {
                return;
            }

            LocalStore localStore = account.getLocalStore();
            localFolder = localStore.getFolder(folder);
            LocalMessage localMessage = localFolder.getMessage(uid);

            if (localMessage == null) {
                return;
            }

            Store remoteStore = account.getRemoteStore();
            remoteFolder = remoteStore.getFolder(folder);
            if (!remoteFolder.exists()) {
                if (!remoteFolder.create(Folder.FolderType.HOLDS_MESSAGES)) {
                    return;
                }
            }
            remoteFolder.open(Folder.OPEN_MODE_RW);
            if (remoteFolder.getMode() != Folder.OPEN_MODE_RW) {
                return;
            }

            Message remoteMessage = null;
            if (!localMessage.getUid().startsWith(K9.LOCAL_UID_PREFIX)) {
                remoteMessage = remoteFolder.getMessage(localMessage.getUid());
            }

            if (remoteMessage == null) {
                if (localMessage.isSet(Flag.X_REMOTE_COPY_STARTED)) {
                    Log.w(K9.LOG_TAG, "Local message with uid " + localMessage.getUid() +
                            " has flag " + Flag.X_REMOTE_COPY_STARTED + " already set, checking for remote message with " +
                            " same message id");
                    String rUid = remoteFolder.getUidFromMessageId(localMessage);
                    if (rUid != null) {
                        Log.w(K9.LOG_TAG, "Local message has flag " + Flag.X_REMOTE_COPY_STARTED + " already set, and there is a remote message with " +
                                " uid " + rUid + ", assuming message was already copied and aborting this copy");

                        String oldUid = localMessage.getUid();
                        localMessage.setUid(rUid);
                        localFolder.changeUid(localMessage);
                        for (MessagingListener l : getListeners()) {
                            l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                        }
                        return;
                    } else {
                        Log.w(K9.LOG_TAG, "No remote message with message-id found, proceeding with append");
                    }
                }

                /*
                 * If the message does not exist remotely we just upload it and then
                 * update our local copy with the new uid.
                 */
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.BODY);
                localFolder.fetch(Collections.singletonList(localMessage) , fp, null);
                String oldUid = localMessage.getUid();
                localMessage.setFlag(Flag.X_REMOTE_COPY_STARTED, true);
                remoteFolder.appendMessages(Collections.singletonList(localMessage));

                localFolder.changeUid(localMessage);
                for (MessagingListener l : getListeners()) {
                    l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                }
            } else {
                /*
                 * If the remote message exists we need to determine which copy to keep.
                 */
                /*
                 * See if the remote message is newer than ours.
                 */
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                remoteFolder.fetch(Collections.singletonList(remoteMessage), fp, null);
                Date localDate = localMessage.getInternalDate();
                Date remoteDate = remoteMessage.getInternalDate();
                if (remoteDate != null && remoteDate.compareTo(localDate) > 0) {
                    /*
                     * If the remote message is newer than ours we'll just
                     * delete ours and move on. A sync will get the server message
                     * if we need to be able to see it.
                     */
                    localMessage.destroy();
                } else {
                    /*
                     * Otherwise we'll upload our message and then delete the remote message.
                     */
                    fp.clear();
                    fp = new FetchProfile();
                    fp.add(FetchProfile.Item.BODY);
                    localFolder.fetch(Collections.singletonList(localMessage), fp, null);
                    String oldUid = localMessage.getUid();

                    localMessage.setFlag(Flag.X_REMOTE_COPY_STARTED, true);

                    remoteFolder.appendMessages(Collections.singletonList(localMessage));
                    localFolder.changeUid(localMessage);
                    for (MessagingListener l : getListeners()) {
                        l.messageUidChanged(account, folder, oldUid, localMessage.getUid());
                    }
                    if (remoteDate != null) {
                        remoteMessage.setFlag(Flag.DELETED, true);
                        if (Account.Expunge.EXPUNGE_IMMEDIATELY == account.getExpungePolicy()) {
                            remoteFolder.expunge();
                        }
                    }
                }
            }
        } finally {
            //closeFolder(remoteFolder);
            //closeFolder(localFolder);
        }
    }

    /* from MessagingController */
    private void putBackground(String description, MessagingListener listener, Runnable runnable) {
        putCommand(mCommands, description, listener, runnable, false);
    }

    /* from MessagingController */
    public Set<MessagingListener> getListeners() {
        return mListeners;
    }
    /* from MessagingController */
    private void putCommand(BlockingQueue<Command> queue, String description, MessagingListener listener, Runnable runnable, boolean isForeground) {
        int retries = 10;
        Exception e = null;
        while (retries-- > 0) {
            try {
                Command command = new Command();
                command.listener = listener;
                command.runnable = runnable;
                command.description = description;
                command.isForeground = isForeground;
                queue.put(command);
                return;
            } catch (InterruptedException ie) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ne) {
                }
                e = ie;
            }
        }
        throw new Error(e);
    }

    /* Copied from MessagingController */
    static AtomicInteger sequencing = new AtomicInteger(0);
    static class Command implements Comparable<Command> {
        public Runnable runnable;

        public MessagingListener listener;

        public String description;

        boolean isForeground;

        int sequence = sequencing.getAndIncrement();

        @Override
        public int compareTo(Command other) {
            if (other.isForeground && !isForeground) {
                return 1;
            } else if (!other.isForeground && isForeground) {
                return -1;
            } else {
                return (sequence - other.sequence);
            }
        }
    }

}