/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import uniarchive.models.IntList;
import uniarchive.models.NameIndex;
import uniarchive.models.OrderedList;
import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.SingletonList;
import uniarchive.models.archive.IMArchive.Conversation.Reply;
import uniarchive.models.archive.IMArchive.Conversation.Speaker;

/**
 * Class for modeling a complete message archive,
 * backed by a Sqlite database.
 * 
 * This class uses static methods in the singleton
 * ArchiveDb class to connect to perform database
 * operations.
 */
public class IMArchive
{
	public static final String IDENTITIES_GROUP_NAME = "Identities";
	
	protected ArchiveDb _db;
	protected String _name;
	protected int _sqlId;
	protected IMArchive _archiveSelf;
	
	// Main tables
	protected List<Group> _groups = new ArrayList<Group>();
	protected Set<Contact> _contacts = new TreeSet<Contact>();
	protected Set<Account> _accounts = new TreeSet<Account>();
	
	// Link tables
	protected Map<Group,OrderedList<Contact>> _groupContacts = new TreeMap<Group,OrderedList<Contact>>();
	protected Map<Contact,OrderedList<Account>> _contactAccounts = new TreeMap<Contact,OrderedList<Account>>();
	
	// Reverse link tables
	protected Map<Contact,Group> _contactGroups = new TreeMap<Contact,Group>();
	protected Map<Account,Contact> _accountContacts = new TreeMap<Account,Contact>();
	
	// By-name index tables
	protected Map<String,Group> _groupsByName = new TreeMap<String,Group>();
	protected Map<String,Contact> _contactsByName = new TreeMap<String,Contact>();
	protected NameIndex<Account> _accountsByName = new NameIndex<Account>();
	
	// By-id index tables
	protected Map<Integer,Group> _groupsById = new TreeMap<Integer,Group>();
	protected Map<Integer,Contact> _contactsById = new TreeMap<Integer,Contact>();
	protected Map<Integer,Account> _accountsById = new TreeMap<Integer,Account>();
	
	// Listeners
	protected List<IMArchiveListener> _listeners = new ArrayList<IMArchiveListener>();
	
	// Other data
	protected int _inLargeChange = 0;
	protected int _noDbUpdates = 0;
	
	/**
	 * Constructor for a temporary archive.
	 */
	public IMArchive() throws SQLException
	{
		this(null);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param name The name of the archive to connect to in the
	 *             sqlite database, or null if this is to be a
	 *             temporary archive 
	 */
	public IMArchive(String name) throws SQLException
	{
		this._archiveSelf = this;
		this._name = name;
		this._db = ArchiveDb.getInstance();
		
		// Check whether the archive exists in the database
		// and retrieve its id; otherwise, create it anew
		this._sqlId = -1;
		if (this._name != null) this._sqlId = this._db.getArchiveByName(name);
		if (this._sqlId == -1)
		{
			this._sqlId = this._db.createArchive(name);
			this._db.createGroup(this._sqlId, IDENTITIES_GROUP_NAME); // Create Identities group
		}
		
		// Now load the archive from the database
		this._loadArchive();
	}
	
	/**
	 * Checks whether this is a temporary (anonymous) archive.
	 * 
	 * @return True if this is a temporary archive, false otherwise
	 */
	public boolean isTemporary()
	{
		return (this._name == null);
	}
	
	/**
	 * Registers a listener for changes in this archive.
	 * 
	 * @param listener A listener object
	 */
	public void addListener(IMArchiveListener listener)
	{
		this._listeners.add(listener);
	}
	
	/**
	 * Unregisters a listener for changes in this archive.
	 * 
	 * @param listener A listener object
	 */
	public void removeListener(IMArchiveListener listener)
	{
		this._listeners.remove(listener);
	}
	
	/**
	 * Returns the name of the archive.
	 * 
	 * @return The name of the archive.
	 */
	public String getName()
	{
		return this._name;
	}
	
	/**
	 * Gets the SQL id assigned to this archive.
	 * 
	 * @return A SQL id.
	 */
	public int getSqlId()
	{
		return this._sqlId;
	}
	
	/**
	 * Gets the group with a given name, if it exists.
	 * 
	 * @param name A group name
	 * @return The corresponding group, or null if none exists
	 */
	public Group getGroupByName(String name)
	{
		return this._groupsByName.get(name);
	}
	
	/**
	 * Gets the contact with a given name, if it exists.
	 * 
	 * @param name A contact name
	 * @return The corresponding contact, or null if none exists
	 */
	public Contact getContactByName(String name)
	{
		return this._contactsByName.get(name);
	}
	
	/**
	 * Gets the account with a given name, if it exists.
	 * 
	 * @param service The account service
	 * @param name The account name
	 * @return The corresponding account, or null if none exists
	 */
	public Account getAccountByName(IMService service, String name)
	{
		return this._accountsByName.getItem(service, name);
	}
	
	/**
	 * Gets the groups defined in this archive.
	 * 
	 * Note: the list will include the special Identities
	 * group as its first element. For a list that omits it,
	 * see getRegularGroups()
	 * 
	 * @return An (immutable) list of groups
	 * @see getRegularGroups()
	 */
	public List<Group> getGroups()
	{
		return Collections.unmodifiableList(this._groups);
	}
	
	/**
	 * Gets the regular groups defined in this archive.
	 * 
	 * Note: the list omits the special Identities group.
	 * For a list that includes it, see getGroups()
	 * 
	 * @return An (immutable) list of groups
	 * @see getGroups()
	 */
	public List<Group> getRegularGroups()
	{
		return Collections.unmodifiableList(this._groups.subList(1, this._groups.size()));
	}
	
	/**
	 * Gets the special Identities group in this archive.
	 * 
	 * @return A reference to the Identities group.
	 */
	public Group getIdentitiesGroup()
	{
		return this._groups.get(0);
	}
	
	/**
	 * Gets all the contacts defined in this archive.
	 * 
	 * @return An (immutable) set of contacts
	 */
	public Set<Contact> getAllContacts()
	{
		return Collections.unmodifiableSet(this._contacts);
	}
	
	/**
	 * Gets all the regular contacts defined in this archive.
	 * 
	 * @return An immutable set of contacts
	 */
	public Set<Contact> getRegularContacts()
	{
		Set<Contact> contacts = new TreeSet<Contact>();
		
		for (Group group : this.getRegularGroups())
			contacts.addAll(group.getContacts());
		
		return contacts;
	}
	
	/**
	 * Gets all the accounts defined in this archive.
	 * 
	 * @return An (immutable) set of accounts
	 */
	public Set<Account> getAllAccounts()
	{
		return Collections.unmodifiableSet(this._accounts);
	}
		
	/**
	 * Returns a list of all the conversations in the archive.
	 * 
	 * By default, the list returned is implemented as a lazy
	 * windowed list with a default window size.
	 * 
	 * @return A list of conversations
	 */
	public List<Conversation> getConversations()
	{
		return this.getConversations(new ConversationsQuery());
	}
	
	/**
	 * Returns a list of all the conversations in the archive
	 * satisfying a given query.
	 * 
	 * By default, the list returned is implemented as a lazy
	 * windowed list with a default window size.
	 * 
	 * @param query A conversations query
	 * @return A list of conversations
	 */
	public List<Conversation> getConversations(ConversationsQuery query)
	{
		return this.getConversationsAsWindowedList(query, 128);
	}
	
	/**
	 * Returns a lazy list of conversations satisfying a given
	 * query, allowing one to iterate through conversations
	 * without loading all of them into memory.
	 * 
	 * @param query A conversations query
	 * @param windowSize The window size (i.e. the maximum number
	 *                   of conversations loaded into memory)
	 */
	public List<Conversation> getConversationsAsWindowedList(ConversationsQuery query, int windowSize)
	{
		return new WindowedConversationList(query, windowSize);
	}
	
	/**
	 * Executes a query in this archive's conversation list and
	 * returns a window in the results.
	 * 
	 * @param query A conversations query
	 * @param offset The window offset
	 * @param limit The window size
	 * @return A list of conversations in the window
	 */
	public List<Conversation> queryConversations(ConversationsQuery query, int offset, int limit) throws SQLException
	{
		// Convert filter and sorting data
		IntList filterIdent = new IntList();
		IntList filterRegular = new IntList();
		IntList filterConv = new IntList();
		for (Account account : query.getIdentityAccountsInFilter()) filterIdent.add(account.sqlId);
		for (Account account : query.getRegularAccountsInFilter()) filterRegular.add(account.sqlId);
		for (Conversation conv : query.filterConversations) filterConv.add(conv.sqlId);
		
		// Compute the ordering fields and process special ordering directives
		String[] sortKeys = new String[query.sortKeys.size()];
		for (int i=0; i<sortKeys.length; i++)
		{
			switch(query.sortKeys.get(i))
			{
			case BY_ACCOUNT: sortKeys[i] = "a.name"; break;
			case BY_CONTACT: sortKeys[i] = "c.name"; break;
			case BY_DATE: sortKeys[i] = "v.date_started"; break;
			case BY_TYPE: sortKeys[i] = "v.is_conference"; break;
			}
		}
		
		// Get conversations (note: without speakers and replies)
		
		ResultSet rset = this._db.getConversations(this._sqlId, filterIdent.toArray(), filterRegular.toArray(),
				filterConv.toArray(), sortKeys, offset, limit);
		
		ArrayList<Conversation> convList = new ArrayList<Conversation>();
		Map<Integer, Conversation> convById = new TreeMap<Integer, Conversation>();
		while (rset.next())
		{
			Conversation conv = new Conversation(
				rset.getDate("date_started"),
				this._accountsById.get(new Integer(rset.getInt("local_account_id"))),
				this._accountsById.get(new Integer(rset.getInt("remote_account_id"))),
				(rset.getInt("is_conference") == 1),
				rset.getInt("id")
			);
			
			conv._replyCount = -1; // invalidate reply count
			convList.add(conv);
			convById.put(new Integer(conv.sqlId), conv);
		}
		
		// Gets speakers
		
		int[] convIds = new int[convList.size()];
		for (int i=0; i<convIds.length; i++) convIds[i] = convList.get(i).sqlId;
		
		rset = this._db.getSpeakers(convIds);
		while (rset.next())
		{
			Conversation conv = convById.get(new Integer(rset.getInt("conversation_id")));
			
			conv._loadSpeaker(
					rset.getString("name"),
					this._accountsById.get(new Integer(rset.getInt("account_id"))),
					rset.getInt("id"));
		}
		
		return convList;
	}
	
	/**
	 * Gets the number of results returned by a query in the
	 * conversations list.
	 * 
	 * @param query A conversations query
	 * @return The number of conversations returned
	 */
	public int countConversationsInQuery(ConversationsQuery query) throws SQLException
	{
		// Convert filter data
		IntList filterIdent = new IntList();
		IntList filterRegular = new IntList();
		IntList filterConv = new IntList();
		for (Account account : query.getIdentityAccountsInFilter()) filterIdent.add(account.sqlId);
		for (Account account : query.getRegularAccountsInFilter()) filterRegular.add(account.sqlId);
		for (Conversation conv : query.filterConversations) filterConv.add(conv.sqlId);
		
		return this._db.countConversations(this._sqlId, filterIdent.toArray(), filterRegular.toArray(), filterConv.toArray());
	}
	
	/**
	 * Counts the number of stored conversations that feature any
	 * in a given set of accounts as speakers or remote parties.
	 * 
	 * @param accounts A list of accounts
	 * @return The number of conversations
	 */
	public int countDependentConversations(List<Account> accounts) throws SQLException
	{
		int[] ids = new int[accounts.size()];
		
		for (int i=0; i<ids.length; i++) ids[i] = accounts.get(i).sqlId;
		
		return this._db.countDependentConversations(ids);
	}
	
	/**
	 * Adds a group to the archive.
	 * 
	 * If the group already exists, nothing happens.
	 * 
	 * @param groupName The name for the new group
	 * @return A reference to the newly created group
	 */
	public Group createGroup(String groupName) throws SQLException
	{
		Group newGroup = this.getGroupByName(groupName);
		if (newGroup != null) return newGroup;
		
		int sqlId = this._db.createGroup(this._sqlId, groupName);
		
		newGroup = new Group(groupName, sqlId);
		this._groups.add(newGroup);
		this._groupsByName.put(groupName, newGroup);
		this._groupsById.put(new Integer(sqlId), newGroup);
		this._groupContacts.put(newGroup, new OrderedList<Contact>());
		
		if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.ADDED_ITEMS, newGroup);
		
		return newGroup;
	}
	
	/**
	 * Adds a conversation to the archive.
	 * 
	 * @param dateStarted The starting date for the conversation (used
	 *                    in sorting conversations)
	 * @param localAccount The local account (i.e. ourselves)
	 * @param remoteAccount The remote account (i.e. the person to whom we are talking)
	 * @param isConference Whether this conversation is a conference or not
	 * @return conversation A reference to the created conversation
	 */
	public Conversation createConversation(Date dateStarted, Account localAccount, Account remoteAccount, boolean isConference) throws SQLException
	{
		int sqlId = this._db.createConversation(this._sqlId, dateStarted, localAccount.sqlId, remoteAccount.sqlId, isConference);
	
		Conversation newConv = new Conversation(dateStarted, localAccount, remoteAccount, isConference, sqlId);
		
		if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.ADDED_ITEMS, newConv);
		
		return newConv;
	}
	
	/**
	 * Adds an identity to the archive.
	 * 
	 * If the identity already exists, nothing happens.
	 * 
	 * @param identName The name for the new identity
	 * @return A reference to the newly created identity contact
	 */
	public Contact createIdentity(String identName) throws SQLException
	{
		return this.getIdentitiesGroup().createContact(identName);
	}
	
	/**
	 * Deletes this archive.
	 * 
	 * After this function is executed, the IMArchive
	 * object should be discarded immediately.
	 */
	public void delete() throws SQLException
	{
		this._fireChangeEvent(IMArchiveEvent.Type.DELETING_ITEMS, this);
		
		this._db.deleteArchive(this._sqlId);
		
		this._fireChangeEvent(IMArchiveEvent.Type.DELETED_ITEMS, this);
	}
	
	/**
	 * Deletes all data in the archive.
	 */
	public void zapData() throws SQLException
	{
		this._db.zapArchiveData(this._sqlId);
		
		this._startLargeChange();
		
		this._groups.clear();
		this._groupsById.clear();
		this._groupsByName.clear();
		this._groupContacts.clear();
		
		this._contacts.clear();
		this._contactsById.clear();
		this._contactsByName.clear();
		this._contactAccounts.clear();
		this._contactGroups.clear();
		
		this._accounts.clear();
		this._accountsById.clear();
		this._accountsByName.clear();
		this._accountContacts.clear();
		
		this.createGroup(IDENTITIES_GROUP_NAME);
		
		this._endLargeChange();
		
		if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.MAJOR_CHANGE, this);
	}
	
	/**
	 * Replaces all the data in this archive with data
	 * from a given archive.
	 * 
	 * @param archive The archive to copy all data from
	 * @param listener An entity that will be notified of any
	 *                 progress in the operation. May be null.
	 */
	public void replaceData(IMArchive archive, ProgressListener listener) throws SQLException
	{
		this.replaceData(archive, false, listener);
	}
	
	/**
	 * Replaces all the data in this archive with data
	 * from a given archive.
	 * 
	 * @param archive The archive to copy all data from
	 * @param accountingOnly If true, only accounts will be copied to
	 *                       the new archive (conversations will be dumped)
	 * @param listener An entity that will be notified of any
	 *                 progress in the operation. May be null.
	 */
	public void replaceData(IMArchive archive, boolean accountingOnly, ProgressListener listener) throws SQLException
	{
		if (archive.getSqlId() == this._sqlId) return;
		
		try
		{
			this._startLargeChange();
			
			// Delete all data in the archive
			if (listener != null) listener.onProgress(new ProgressEvent("Deleting current archive data...", 0, -1));
			this.zapData();
			
			// Copy all groups, contacts and accounts
			if (listener != null) listener.onProgress(new ProgressEvent("Copying accounting data...", 0, -1));
			for (Group remoteGroup : archive.getGroups())
			{
				Group localGroup = this.createGroup(remoteGroup.name);
				
				for (Contact remoteContact : remoteGroup.getContacts())
				{
					Contact localContact = localGroup.createContact(remoteContact.name);
					
					for (Account remoteAccount : remoteContact.getAccounts())
						localContact.createAccount(remoteAccount.service, remoteAccount.name);
				}
			} 
			
			// Copy conversations
			if (!accountingOnly)
			{
				List<Conversation> remoteConversations = archive.getConversations();
				
				int processed = 0;
				int total = remoteConversations.size();
				if (listener != null) listener.onProgress(new ProgressEvent("Copying conversations...", processed, total));
				
				for (Conversation remoteConv : remoteConversations)
				{
					Conversation localConv = this.createConversation(remoteConv.dateStarted,
							this.getAccountByName(remoteConv.localAccount.service, remoteConv.localAccount.name),
							this.getAccountByName(remoteConv.remoteAccount.service, remoteConv.remoteAccount.name),
							remoteConv.isConference);
					
					for (Speaker speaker : remoteConv.getSpeakers())
						localConv.addSpeaker(speaker.name, this.getAccountByName(speaker.account.service, speaker.account.name));
					
					for (Reply reply : remoteConv.getReplies())
						localConv.addReply(reply.date,
								(reply.speaker != null) ?
										localConv.getSpeakerByName(reply.speaker.name) :
										null,
								reply.text);
					
					processed++;
					if (listener != null) listener.onProgress(new ProgressEvent("Copying conversations...", processed, total));
				}
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.toString());
		}
		finally
		{
			this._endLargeChange();
			
			if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.MAJOR_CHANGE, this);
		}
	}
	
	/**
	 * Merges a given archive into this one.
	 * 
	 * @param archive The archive to merge data from
	 * @param listener An entity that will be notified of any
	 *                 progress in the operation. May be null.
	 */
	public void mergeData(IMArchive archive, ProgressListener listener) throws SQLException
	{
		this.mergeData(archive, false, listener);
	}
	
	/**
	 * Merges a given archive into this one.
	 * 
	 * @param archive The archive to merge data from
	 * @param accountingOnly If true, only accounts will be merged into
	 *                       the new archive (conversations will be ignored)
	 * @param listener An entity that will be notified of any
	 *                 progress in the operation. May be null.
	 */
	public void mergeData(IMArchive archive, boolean accountingOnly, ProgressListener listener) throws SQLException
	{
		if (archive.getSqlId() == this._sqlId) return;
		
		try
		{
			this._startLargeChange();
			
			// Merge groups, contacts and accounts
			if (listener != null) listener.onProgress(new ProgressEvent("Merging accounting data...", 0, -1));
			
			// First, copy all groups that are initially empty (only groups that become
			// empty through the absorbtion of contacts will fail to appear in the final
			// archive)
			for (Group remoteGroup : archive.getGroups())
				if (remoteGroup.getContacts().isEmpty()) this.createGroup(remoteGroup.name);
				
			// Now process every remote account
			Map<Account,Account> accountsMap = new TreeMap<Account,Account>();
			for (Account remoteAccount : archive.getAllAccounts())
			{
				boolean isIdent = remoteAccount.isIdentityAccount();
				String newAccountName = remoteAccount.name;
				
				// Check whether the account already exists in the local archive
				Account localAccount = this.getAccountByName(remoteAccount.service, newAccountName);
				if (localAccount != null) // Account already exists
				{
					// But is it of the same type (identity vs. regular)?
					if (localAccount.isIdentityAccount() == isIdent) // Yes
					{
						// Account completely absorbed; No further processing is needed
						accountsMap.put(remoteAccount, localAccount);
						continue;
					}
					else // No; a new account will have to be created, with a similar name
					{
						while (true)
						{
							newAccountName += "*";
							localAccount = this.getAccountByName(remoteAccount.service, newAccountName);
							if ((localAccount == null) || (localAccount.isIdentityAccount() == isIdent)) break;
						}
					}
				}
				
				// If we have reached this point, a new corresponding account
				// will have to be created.
				String newContactName = remoteAccount.getContact().name;
				
				// Check whether the contact already exists in the local archive
				Contact localContact = this.getContactByName(newContactName);
				if (localContact != null) // Contact already exists
				{
					// But is it of the same type (identity vs. regular)?
					if (localContact.isIdentity() == isIdent) // Yes
					{
						// The account is absorbed in the existing
						// contact; no further processing is needed
						localAccount = localContact.createAccount(remoteAccount.service, newAccountName);
						accountsMap.put(remoteAccount, localAccount);
						continue;
					}
					else // No; a new contact will have to be created, with a similar name
					{
						while (true)
						{
							newContactName += "*";
							localContact = this.getContactByName(newContactName);
							if ((localContact == null) || (localContact.isIdentity() == isIdent)) break;
						}
					}
				}
				
				// If we have reached this point, a new corresponding contact
				// will have to be created
				localAccount = this.createGroup(remoteAccount.getContact().getGroup().name)
					.createContact(newContactName)
					.createAccount(remoteAccount.service, newAccountName);
				
				accountsMap.put(remoteAccount, localAccount);
			}
			
			// Merge conversations
			if (!accountingOnly)
			{
				// First, the conversations are compared in a mergesort-like
				// procedure, so as to allow identical or outdated conversations in
				// both archives to be overwritten.
				//
				// Note: the conversations are sorted by date in both archives.
				// Each current conversation is matched against all new conversations
				// happening on the same date. We're using the fact that there can't
				// be that many conversations on that exact date.
				ConversationsQuery query = new ConversationsQuery();
				query.sortKeys.clear();
				query.sortKeys.add(ConversationsQuery.SortKey.BY_DATE);
				
				List<Conversation> localConversations = this.getConversations(query);
				List<Conversation> remoteConversations = archive.getConversations(query);
				
				int total = localConversations.size() + remoteConversations.size();
				int processed = 0;
				if (listener != null) listener.onProgress(new ProgressEvent("Merging conversations...", processed, total));
				
				int remoteIndex = 0;
				IntList dontKeepIds = new IntList();
				IntList dontImportIds = new IntList();
				for (Conversation localConv : localConversations)
				{
					Date localDate = localConv.dateStarted;
					
					// Advance the pointer in the remote conversations list
					// until we get to a date equal to or past the local
					// conversation's date
					while (remoteIndex < remoteConversations.size())
					{
						if (!remoteConversations.get(remoteIndex).dateStarted.before(localDate)) break;
						remoteIndex++;
					}
					
					// Check that we have stopped on a remote conversation
					// matching the local one's date
					if ((remoteIndex < remoteConversations.size()) &&
						remoteConversations.get(remoteIndex).dateStarted.equals(localDate))
					{
						// Now compare the local conversation to all those that
						// share the same date
						for (int remoteIdx2 = remoteIndex; remoteIdx2 < remoteConversations.size(); remoteIdx2++)
						{
							Conversation remoteConv = remoteConversations.get(remoteIdx2);
							if (remoteConv.dateStarted.after(localDate)) break;
							
							// Compare conversations in detail
							if (
									(localConv.localAccount.equals(accountsMap.get(remoteConv.localAccount))) &&
									(localConv.remoteAccount.equals(accountsMap.get(remoteConv.remoteAccount))))
							{
								// If the remote conversation has more replies, it overwrites
								// the local one, otherwise it is ignored
								if (remoteConv.getReplyCount() > localConv.getReplyCount())
									dontKeepIds.add(localConv.sqlId);
								else
									dontImportIds.add(remoteConv.sqlId);	
							}
						}
					}
					
					processed++;
					if (listener != null) listener.onProgress(new ProgressEvent("Merging conversations...", processed, total));
				}
				
				// Delete the overwritten conversations
				this._db.deleteConversations(dontKeepIds.toArray());
				
				// Now copy the new conversations
				dontImportIds.sort();
				for (Conversation remoteConv : remoteConversations)
				{
					if (!dontImportIds.contains(remoteConv.sqlId))
					{
						Conversation localConv = this.createConversation(remoteConv.dateStarted,
								accountsMap.get(remoteConv.localAccount),
								accountsMap.get(remoteConv.remoteAccount),
								remoteConv.isConference);
						
						for (Speaker speaker : remoteConv.getSpeakers())
							localConv.addSpeaker(speaker.name, accountsMap.get(speaker.account));
						
						for (Reply reply : remoteConv.getReplies())
							localConv.addReply(reply.date,
									(reply.speaker != null) ?
											localConv.getSpeakerByName(reply.speaker.name) :
											null,
									reply.text);
					}
					
					processed++;
					if (listener != null) listener.onProgress(new ProgressEvent("Merging conversations...", processed, total));
				}
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.toString());
		}
		finally
		{
			this._endLargeChange();
			
			if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.MAJOR_CHANGE, this);
		}
	}
	
	/**
	 * Searches for conversations that feature a given pattern in their
	 * replies.
	 * 
	 * @param query A query for selecting the conversations that are
	 *              to be searched
	 * @param pattern A regex pattern to look for in the reply text
	 * @param maxResults A limit on the number of results returned
	 * @param listener An entity that will be notified of any
	 *                 progress in the operation. May be null.
	 */
	public List<Conversation> searchConversations(ConversationsQuery query, Pattern pattern,
			int maxResults, ProgressListener listener) throws SQLException
	{
		List<Conversation> results = new ArrayList<Conversation>();
		
		listener.onProgress(new ProgressEvent("Searching...", 0, 1));
		
		List<Conversation> conversations = this.getConversations(query);
		
		int processed = 0;
		int total = conversations.size();
		
		for (Conversation conv : conversations)
		{
			listener.onProgress(new ProgressEvent("Searching...", processed, total));
			processed++;
			
			for (Reply reply : conv.getReplies())
				if (pattern.matcher(reply.text).find())
				{
					results.add(conv);
					break;
				}
			
			if (results.size() >= maxResults) break;
		}
		listener.onProgress(new ProgressEvent("Searching...", total, total));
		
		return results;
	}
	
	/**
	 * Loads the in-memory indexes of this structure with
	 * the corresponding data in the underlying database
	 * object.
	 */
	protected void _loadArchive() throws SQLException
	{
		try
		{
			this._startLargeChange();
			
			// Load groups
			
			this._groups.clear();
			this._groupsById.clear();
			this._groupsByName.clear();
			this._groupContacts.clear();
			
			ResultSet rset = this._db.loadGroups(this._sqlId);
			while (rset.next())
			{
				Group group = new Group(rset.getString("name"), rset.getInt("id"));
				this._groups.add(group);
				this._groupsById.put(new Integer(group.sqlId), group);
				this._groupsByName.put(group.name, group);
				this._groupContacts.put(group, new OrderedList<Contact>());
			}
			
			// Load contacts
			
			this._contacts.clear();
			this._contactsById.clear();
			this._contactsByName.clear();
			this._contactAccounts.clear();
			this._contactGroups.clear();
			
			rset = this._db.loadContacts(this._sqlId);
			while (rset.next())
			{
				Contact contact = new Contact(rset.getString("name"), rset.getInt("id"));
				this._contacts.add(contact);
				this._contactsById.put(new Integer(contact.sqlId), contact);
				this._contactsByName.put(contact.name, contact);
				this._contactAccounts.put(contact, new OrderedList<Account>());
				
				Group parentGroup = this._groupsById.get(new Integer(rset.getInt("group_id")));
				this._groupContacts.get(parentGroup).add(contact);
				this._contactGroups.put(contact, parentGroup);
			}
			
			// Load accounts
			
			this._accounts.clear();
			this._accountsById.clear();
			this._accountsByName.clear();
			this._accountContacts.clear();
			
			rset = this._db.loadAccounts(this._sqlId);
			while (rset.next())
			{
				Account account = new Account(this._db.getServiceById(rset.getInt("service_id")), rset.getString("name"), rset.getInt("id"));
				this._accounts.add(account);
				this._accountsById.put(new Integer(account.sqlId), account);
				this._accountsByName.addItem(account.service, account.name, account);
				
				Contact parentContact = this._contactsById.get(new Integer(rset.getInt("contact_id")));
				this._contactAccounts.get(parentContact).add(account);
				this._accountContacts.put(account, parentContact);
			}
		}
		finally
		{
			this._endLargeChange();
			
			if (this._inLargeChange == 0) this._fireChangeEvent(IMArchiveEvent.Type.MAJOR_CHANGE, this);
		}
	}

	/**
	 * Enters a state in which changes in the archive
	 * are not reported immediately via events, but
	 * rather expressed in a single "major change"
	 * event at the end.
	 */
	protected void _startLargeChange()
	{
		this._inLargeChange++;
	}
	
	/**
	 * Exits the "large change" state.
	 */
	protected void _endLargeChange()
	{
		this._inLargeChange--;
	}
	
	/**
	 * Enters a state in which database updates are not
	 * performed (operations take effect on indexes only).
	 */
	protected void _startNoDbUpdates()
	{
		this._noDbUpdates++;
	}
	
	/**
	 * Ends the "no database updates" state.
	 */
	protected void _endNoDbUpdates()
	{
		this._noDbUpdates--;
	}
	
	/**
	 * Notifies any registered listeners of a change in the
	 * archive content.
	 * 
	 * @param type The event type
	 * @param items A list of affected items
	 */
	protected void _fireChangeEvent(IMArchiveEvent.Type type, List<Object> items)
	{
		IMArchiveEvent event = new IMArchiveEvent(type, items);
		
		// Note: we will iterate over a copy of the listeners list,
		// as one possible response to the event is to unregister
		// listeners (e.g. controls will disconnect if the archive
		// is being deleted)
		IMArchiveListener[] listeners = this._listeners.toArray(new IMArchiveListener[this._listeners.size()]);
		for (IMArchiveListener listener : listeners) listener.archiveChanged(event);
	}
	
	/**
	 * Like _fireChangeEvent(.., List<Object>), for a single item event.
	 * 
	 * @param type The event type
	 * @param item The affected item
	 */
	protected void _fireChangeEvent(IMArchiveEvent.Type type, Object item)
	{
		this._fireChangeEvent(type, new SingletonList<Object>(item));
	}
	
	/**
	 * Class for modeling an instant messaging group reference.
	 */
	public class Group implements Comparable<Group>
	{
		public final String name;
		public final int sqlId;
		
		/**
		 * Constructor.
		 * 
		 * @param name The name of the group
		 * @param sqlId The SQL id of the group
		 */
		private Group(String name, int sqlId)
		{
			this.name = name;
			this.sqlId = sqlId;
		}
		
		/**
		 * Gets the archive to which this group belongs.
		 */
		public IMArchive getArchive()
		{
			return _archiveSelf;
		}
		
		/**
		 * Gets this group's contacts.
		 *
		 * @return An (immutable) list of contacts.
		 */
		public List<Contact> getContacts()
		{
			return Collections.unmodifiableList(_groupContacts.get(this));
		}
		
		/**
		 * Checks whether this is the Identities group.
		 * 
		 * @return True if this is the identities group,
		 *         false otherwise.
		 */
		public boolean isIdentitiesGroup()
		{
			return this.equals(_groups.get(0));
		}
		
		/**
		 * Adds a contact this group.
		 * 
		 * If the contact already exists in another group,
		 * nothing happens.
		 * 
		 * @param contactName The name for the new contact
		 * @return A reference to the newly created contact
		 */
		public Contact createContact(String contactName) throws SQLException
		{
			Contact newContact = getContactByName(contactName);
			if (newContact != null) return newContact;

			int sqlId = _db.createContact(this.sqlId, contactName);
			
			newContact = new Contact(contactName, sqlId);
			_contacts.add(newContact);
			_contactsByName.put(contactName, newContact);
			_contactsById.put(new Integer(sqlId), newContact);
			_contactAccounts.put(newContact, new OrderedList<Account>());
			
			_groupContacts.get(this).add(newContact);
			_contactGroups.put(newContact, this);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.ADDED_ITEMS, newContact);
			
			return newContact;
		}
		
		/**
		 * Moves this group to another position in the main list.
		 * 
		 * Note: the Identities group cannot be moved; nor may another
		 * group be moved before the Identities group.
		 * 
		 * @param moveBefore The group immediately before which the group
		 *                   will be repositioned. If this is null, the
		 *                   group will be moved to the end of the list
		 */
		public void move(Group moveBefore) throws SQLException
		{
			if (this.isIdentitiesGroup()) throw new RuntimeException("The Identities group may not be moved.");
			if ((moveBefore != null) && (moveBefore.isIdentitiesGroup()))
				throw new RuntimeException("No group may be moved before the Identities group.");
			
			int oldIndex = _groups.indexOf(this);
			int mbIndex = (moveBefore != null) ? _groups.indexOf(moveBefore) : _groups.size();
			if ((mbIndex == oldIndex) || (mbIndex == oldIndex+1)) return;
			int newIndex = (mbIndex < oldIndex) ? mbIndex : mbIndex-1;
			
			_db.moveGroup(this.sqlId, newIndex);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVING_ITEMS, this);
			
			_groups.remove(oldIndex);
			if (moveBefore == null)
				_groups.add(this);
			else
				_groups.add(_groups.indexOf(moveBefore), this);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVED_ITEMS, this);
		}
		
		/**
		 * Merges another group into this one.
		 * 
		 * @param group The group to merge. The Identities group
		 *              may not be merged into another.
		 */
		public void merge(Group group) throws SQLException
		{
			if (this.isIdentitiesGroup()) throw new RuntimeException("No group may be merged into the Identities group.");
			if (group.isIdentitiesGroup()) throw new RuntimeException("The Identities group may not be merged into another.");
			if (group.equals(this)) return;
			
			_db.mergeGroup(group.sqlId, this.sqlId);
			
			List<Object> items = new ArrayList<Object>(_groupContacts.get(group));
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVING_ITEMS, items);
			
			for (Contact contact : _groupContacts.get(group))
			{
				_groupContacts.get(this).add(contact);
				_contactGroups.put(contact, this);
			}
			_groupContacts.get(group).clear();
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVED_ITEMS, items);
			
			group.delete();
		}
		
		/**
		 * Renames this group.
		 * 
		 * @param newName The new name of the group. An exception
		 *                will be thrown if the name is already taken.
		 *                The Identities group may not be renamed.
		 * @return A reference to the renamed group
		 */
		public Group rename(String newName) throws SQLException
		{
			if (this.isIdentitiesGroup()) throw new RuntimeException("The Identities group may not be renamed.");
			if (this.name.equals(newName)) return this;
			if (newName.isEmpty()) throw new RuntimeException("You must specify a non-empty name.");
			if (getGroupByName(newName) != null) throw new RuntimeException("That name is already taken.");
			
			_db.renameGroup(this.sqlId, newName);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.UPDATING_ITEMS, this);
			
			Group newGroup = new Group(newName, this.sqlId);
			_groups.set(_groups.indexOf(this), newGroup);
			_groupsByName.remove(this.name);
			_groupsByName.put(newName, newGroup);
			_groupsById.put(this.sqlId, newGroup);
			OrderedList<Contact> contacts = _groupContacts.get(this);
			_groupContacts.remove(this);
			_groupContacts.put(newGroup, contacts);
			for (Contact contact : contacts) _contactGroups.put(contact, newGroup);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.UPDATED_ITEMS, newGroup);
			
			return newGroup;
		}
		
		/**
		 * Deletes this group and all dependent contacts,
		 * accounts and conversations.
		 * 
		 * The Identities group may not be deleted.
		 */
		public void delete() throws SQLException
		{
			if (this.isIdentitiesGroup()) throw new RuntimeException("The Identities group may not be deleted.");
			
			_db.deleteGroups(new int[] { this.sqlId });
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.DELETING_ITEMS, this);

			_startLargeChange();
			_startNoDbUpdates();
			List<Contact> contacts = new ArrayList<Contact>(this.getContacts());
			for (Contact contact : contacts) contact.delete();
			_endNoDbUpdates();
			_endLargeChange();
			
			_groups.remove(this);
			_groupsByName.remove(this.name);
			_groupsById.remove(this.sqlId);
			_groupContacts.remove(this);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.DELETED_ITEMS, this);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
		}
		
		@Override
		public int compareTo(Group other)
		{
			return this.name.compareTo(other.name);
		}
		
		public boolean equals(Group other)
		{
			return this.name.equals(other.name);
		}
		
		@Override
		public String toString()
		{
			return "Group '"+this.name+"'";
		}
	}
	
	/**
	 * Class for modeling a contact reference.
	 */
	public class Contact implements Comparable<Contact>
	{
		public final String name;
		public final int sqlId;
		
		/**
		 * Constructor.
		 * 
		 * @param name The name of the contact
		 * @param sqlId The SQL id of the contact
		 */
		private Contact(String name, int sqlId)
		{
			this.name = name;
			this.sqlId = sqlId;
		}
		
		/**
		 * Gets the archive to which this contact belongs.
		 */
		public IMArchive getArchive()
		{
			return _archiveSelf;
		}
		
		/**
		 * Gets the group to which this contact belongs.
		 * 
		 * @return An IM group
		 */
		public Group getGroup()
		{
			return _contactGroups.get(this);
		}
		
		/**
		 * Checks whether this is an identity contact (it
		 * belongs to the Identities group).
		 * 
		 * @return True if this is an identity contact, false
		 *         otherwise.
		 */
		public boolean isIdentity()
		{
			return this.getGroup().isIdentitiesGroup();
		}
		
		/**
		 * Gets this contact's accounts.
		 * 
		 * @return An (immutable) list of accounts
		 */
		public List<Account> getAccounts()
		{
			return Collections.unmodifiableList(_contactAccounts.get(this));
		}
		
		/**
		 * Adds an account to this contact.
		 * 
		 * If the account already exists in another contact,
		 * nothing happens.
		 * 
		 * @param service The account service
		 * @param name The name of the account
		 * @return A reference to the created account
		 */
		public Account createAccount(IMService service, String name) throws SQLException
		{
			Account newAccount = getAccountByName(service, name);
			if (newAccount != null) return newAccount;
			
			int sqlId = _db.createAccount(this.sqlId, _db.getServiceId(service), name);
			
			newAccount = new Account(service, name, sqlId);
			_accounts.add(newAccount);
			_accountsByName.addItem(newAccount.service, newAccount.name, newAccount);
			_accountsById.put(new Integer(sqlId), newAccount);
			_contactAccounts.get(this).add(newAccount);
			_accountContacts.put(newAccount, this);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.ADDED_ITEMS, newAccount);
			
			return newAccount;
		}
		
		/**
		 * Moves this contact to another group.
		 * 
		 * @param newGroup The contact's new parent group
		 */
		public void move(Group newGroup) throws SQLException
		{
			if (this.isIdentity()) throw new RuntimeException("Identity contacts may not be moved.");
			if (newGroup.isIdentitiesGroup()) throw new RuntimeException("A regular contact may not be moved in the Identities group.");
			
			Group parent = this.getGroup();
			if (parent.equals(newGroup)) return;
			
			_db.setContactGroup(this.sqlId, newGroup.sqlId);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVING_ITEMS, this);
			
			_groupContacts.get(parent).remove(this);
			_groupContacts.get(newGroup).add(this);
			_contactGroups.put(this, newGroup);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.MOVED_ITEMS, this);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
		}
		
		/**
		 * Merges another contact into this one.
		 * 
		 * @param contact The contact to merge
		 */
		public void merge(Contact contact) throws SQLException
		{
			if (this.isIdentity() && !contact.isIdentity()) throw new RuntimeException("Regular contacts may not be merged into identities.");
			if (!this.isIdentity() && contact.isIdentity()) throw new RuntimeException("Identities may not be merged into regular contacts.");
			
			if (contact.equals(this)) return;
			
			_db.mergeContact(contact.sqlId, this.sqlId);
			
			List<Object> items = new ArrayList<Object>(_contactAccounts.get(contact));
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVING_ITEMS, items);
			
			for (Account account : _contactAccounts.get(contact))
			{
				_contactAccounts.get(this).add(account);
				_accountContacts.put(account, this);
			}
			_contactAccounts.get(contact).clear();
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.MOVED_ITEMS, items);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
			
			contact.delete();
		}
		
		/**
		 * Renames this contact.
		 * 
		 * @param newName The new name of the contact. An exception
		 *                will be thrown if the name is already taken.
		 * @return A reference to the renamed contact
		 */
		public Contact rename(String newName) throws SQLException
		{
			if (this.name.equals(newName)) return this;
			if (newName.isEmpty()) throw new RuntimeException("You must specify a non-empty name.");
			if (getContactByName(newName) != null) throw new RuntimeException("That name is already taken.");
			
			_db.renameContact(this.sqlId, newName);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.UPDATING_ITEMS, this);
			
			Group parent = this.getGroup();
			Contact newContact = new Contact(newName, this.sqlId);
			_contacts.remove(this);
			_contacts.add(newContact);
			_contactsByName.remove(this.name);
			_contactsByName.put(newName, newContact);
			_contactsById.put(this.sqlId, newContact);
			_groupContacts.get(parent).remove(this);
			_groupContacts.get(parent).add(newContact);
			_contactGroups.remove(this);
			_contactGroups.put(newContact, parent);
			OrderedList<Account> accounts = _contactAccounts.get(this);
			_contactAccounts.remove(this);
			_contactAccounts.put(newContact, accounts);
			for (Account account : accounts) _accountContacts.put(account, newContact);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_ITEMS, newContact);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
			
			return newContact;
		}
		
		/**
		 * Deletes this contact and all dependent accounts and
		 * conversations.
		 */
		public void delete() throws SQLException
		{
			_db.deleteContacts(new int[] { this.sqlId });
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.DELETING_ITEMS, this);

			_startLargeChange();
			_startNoDbUpdates();
			List<Account> accounts = new ArrayList<Account>(this.getAccounts());
			for (Account account : accounts) account.delete();
			_endNoDbUpdates();
			_endLargeChange();
			
			_contacts.remove(this);
			_contactsByName.remove(this.name);
			_contactsById.remove(this.sqlId);
			_groupContacts.get(this.getGroup()).remove(this);
			_contactGroups.remove(this);
			_contactAccounts.remove(this);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.DELETED_ITEMS, this);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
		}
		
		@Override
		public int compareTo(Contact other)
		{
			return this.name.compareTo(other.name);
		}
		
		public boolean equals(Contact other)
		{
			return this.name.equals(other.name);
		}
		
		@Override
		public String toString()
		{
			return "Contact '"+this.name+"'";
		}
	}
	
	/**
	 * Class for modeling an account reference.
	 */
	public class Account implements Comparable<Account>
	{
		public final IMService service;
		public final String name;
		public final int sqlId;
		
		/**
		 * Constructor.
		 * 
		 * @param service The IM service for this account
		 * @param name The account name within the specified service
		 * @param sqlId The SQL id of the account
		 */
		private Account(IMService service, String name, int sqlId)
		{
			this.service = service;
			this.name = name;
			this.sqlId = sqlId;
		}
		
		/**
		 * Gets the archive to which this account belongs.
		 */
		public IMArchive getArchive()
		{
			return _archiveSelf;
		}
		
		/**
		 * Gets the contact to which this account belongs.
		 * 
		 * @return An IM contact, or null if the account does not
		 *         belong to any contact in the archive
		 */
		public Contact getContact()
		{
			return _accountContacts.get(this);
		}
		
		/**
		 * Checks whether this account belongs to an
		 * identity contact.
		 * 
		 * @return True if the account belongs to an identity
		 */
		public boolean isIdentityAccount()
		{
			return this.getContact().isIdentity();
		}
		
		/**
		 * Moves this account under another contact.
		 * 
		 * @param newContact The account's new parent contact
		 */
		public void move(Contact newContact) throws SQLException
		{
			if (this.isIdentityAccount() != newContact.isIdentity())
				throw new RuntimeException("Identity contacts and regular contacts may not trade accounts.");
			
			Contact parent = this.getContact();
			if (parent.equals(newContact)) return;
			
			_db.setContactGroup(this.sqlId, newContact.sqlId);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.MOVING_ITEMS, this);
			
			_contactAccounts.get(parent).remove(this);
			_contactAccounts.get(newContact).add(this);
			_accountContacts.put(this, newContact);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.MOVED_ITEMS, this);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
		}
		
		/**
		 * Renames this account.
		 * 
		 * @param newName The new name. An exception will be thrown
		 *                if the name is already taken. 
		 * @return A reference to the renamed account
		 */
		public Account rename(String newName) throws SQLException
		{
			if (this.name.equals(newName)) return this;
			if (newName.isEmpty()) throw new RuntimeException("You must specify a non-empty name.");
			if (getContactByName(newName) != null) throw new RuntimeException("That name is already taken.");
			
			_db.renameAccount(this.sqlId, newName);
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.UPDATING_ITEMS, this);
			
			Account newAccount = new Account(this.service, newName, this.sqlId);
			_accounts.remove(this);
			_accounts.add(newAccount);
			_accountsByName.removeItem(this.service, this.name);
			_accountsByName.addItem(this.service, newName, newAccount);
			_accountsById.put(this.sqlId, newAccount);
			
			Contact contact = _accountContacts.get(this);
			_contactAccounts.get(contact).remove(this);
			_contactAccounts.get(contact).add(newAccount);
			_accountContacts.remove(this);
			_accountContacts.put(newAccount, contact);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_ITEMS, newAccount);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
			
			return newAccount;
		}
		
		/**
		 * Deletes this account and all related conversations.
		 */
		public void delete() throws SQLException
		{
			if (_noDbUpdates == 0) _db.deleteAccounts(new int[] { this.sqlId });
			
			if (_inLargeChange == 0) _fireChangeEvent(IMArchiveEvent.Type.DELETING_ITEMS, this);
			
			_accounts.remove(this);
			_accountsByName.removeItem(this.service, this.name);
			_accountsById.remove(this.sqlId);
			_contactAccounts.get(this.getContact()).remove(this);
			_accountContacts.remove(this);
			
			if (_inLargeChange == 0)
			{
				_fireChangeEvent(IMArchiveEvent.Type.DELETED_ITEMS, this);
				_fireChangeEvent(IMArchiveEvent.Type.UPDATED_CONVERSATIONS, new ArrayList<Object>());
			}
		}

		@Override
		public int compareTo(Account other)
		{
			if (other.service != this.service) return this.service.compareTo(other.service);
			
			return this.name.compareTo(other.name);
		}
		
		public boolean equals(Account other)
		{
			return (this.service == other.service) && (this.name.equals(other.name));
		}
		
		@Override
		public String toString()
		{
			return this.service.shortName+":"+this.name;
		}
	}
	
	/**
	 * Class for modeling a reference to a conversation.
	 */
	public class Conversation implements Comparable<Conversation>
	{
		public final Date dateStarted;
		public final Account localAccount;
		public final Account remoteAccount;
		public final boolean isConference;
		public final int sqlId;
		
		protected List<Speaker> _speakers;
		protected int _replyCount;

		/**
		 * Constructor.
		 * 
		 * @param dateStarted The starting date for the conversation (used
		 *                    in sorting conversations)
		 * @param localAccount The local account (i.e. ourselves)
		 * @param remoteAccount The remote account (i.e. the person to whom we are talking)
		 * @param isConference Whether this conversation is a conference or not
		 * @param sqlId The SQL id of the conversation
		 */
		private Conversation(Date dateStarted, Account localAccount, Account remoteAccount, boolean isConference, int sqlId)
		{
			this.dateStarted = dateStarted;
			this.localAccount = localAccount;
			this.remoteAccount = remoteAccount;
			this.isConference = isConference;
			this.sqlId = sqlId;
			
			this._speakers = new ArrayList<Speaker>();
			this._replyCount = 0;
		}
		
		/**
		 * Gets the archive to which this account belongs.
		 */
		public IMArchive getArchive()
		{
			return _archiveSelf;
		}
		
		/**
		 * Gets the speakers in this conversation.
		 * 
		 * @return An (immutable) list of speakers
		 */
		public List<Speaker> getSpeakers()
		{
			return Collections.unmodifiableList(this._speakers);
		}
		
		/**
		 * Gets the number of replies in this conversation.
		 * 
		 * @return The number of replies
		 */
		public int getReplyCount() throws SQLException
		{
			if (this._replyCount == -1) this._replyCount = _db.countReplies(this.sqlId);
					
			return this._replyCount;
		}
		
		/**
		 * Gets the replies in this conversation.
		 * 
		 * @return An immutable list of replies
		 */
		public List<Reply> getReplies() throws SQLException
		{
			ResultSet rset = _db.getReplies(this.sqlId);
			
			List<Reply> replies = new ArrayList<Reply>();
			while (rset.next())
			{
				Reply reply = new Reply(
						rset.getDate("reply_date"),
						this.getSpeakerById(rset.getInt("speaker_id")),
						rset.getString("content"),
						rset.getInt("id"));
				
				replies.add(reply);
			}
			
			return replies;
		}
		
		/**
		 * Adds a speaker to this conversation.
		 * 
		 * @param name The name of the speaker
		 * @param account The account used by the speaker
		 * @return A reference to the newly added speaker
		 */
		public Speaker addSpeaker(String name, Account account) throws SQLException
		{
			int sqlId = _db.createSpeaker(this.sqlId, name, account.sqlId);
			
			Speaker speaker = new Speaker(name, account,sqlId);
			this._speakers.add(speaker);
			
			return speaker;
		}
		
		/**
		 * Adds a reply to this conversation.
		 * 
		 * @param replyDate The date of the reply
		 * @param speaker A reference to the speaker making the reply
		 * @param content The text content of the reply
		 * @return A reference to the newly added reply
		 */
		public Reply addReply(Date replyDate, Speaker speaker, String content) throws SQLException
		{
			int sqlId = _db.createReply(this.sqlId, this._replyCount, replyDate, (speaker != null) ? speaker.sqlId : -1, content);
			
			Reply reply = new Reply(replyDate, speaker, content, sqlId);
			this._replyCount++;
			
			return reply;
		}
		
		/**
		 * Gets the speaker having a given name.
		 * 
		 * @param name The name of the speaker to look for
		 * @return A reference to the speaker, of null if it
		 *         could not be found
		 */
		public Speaker getSpeakerByName(String name)
		{
			for (Speaker speaker : this._speakers) if (speaker.name.equals(name)) return speaker;
			
			return null;
		}
		
		/**
		 * Gets the speaker having a given SQL id.
		 * 
		 * @param sqlId The sql ID of the speaker to look for
		 * @return A reference to the speaker, of null if it
		 *         could not be found
		 */
		public Speaker getSpeakerById(int sqlId)
		{
			if (sqlId == -1) return null;
			
			for (Speaker speaker : this._speakers) if (speaker.sqlId == sqlId) return speaker;
			
			return null;
		}
		
		/**
		 * Returns a list of distinct participants in this conversation
		 * (some speakers may map to the same contact). It is
		 * guaranteed that the first participant in the list will
		 * be the identity contact corresponding to the local account. 
		 * 
		 * @return A list of distinct participants
		 */
		public List<Contact> getDistinctParticipants()
		{
			ArrayList<Contact> participants = new ArrayList<Contact>();
			
			// Add the local identity contact first
			participants.add(this.localAccount.getContact());
			
			// Add the remote account (note that it might not appear as a speaker!)
			if (!participants.contains(this.remoteAccount.getContact()))
				participants.add(this.remoteAccount.getContact());
			
			// Add other participants (useful for conferences)
			if (this.isConference)
				for (Speaker speaker : this._speakers)
				{
					if (!participants.contains(speaker.account.getContact()))
						participants.add(speaker.account.getContact());
				}
			
			return participants;
		}
		
		/**
		 * Checks whether this conversation makes use of
		 * any in a given list of accounts as a speaker or
		 * remote party.
		 * 
		 * @param accounts A set of accounts to check against 
		 * @return True if the conversation depends upon said
		 *         accounts, false otherwise
		 */
		public boolean usesAccounts(Set<Account> accounts)
		{
			if (accounts.contains(this.remoteAccount)) return true;
			
			for (Speaker speaker : this._speakers)
				if (accounts.contains(speaker.account) && !speaker.account.equals(this.localAccount)) return true;
			
			return false;
		}
		
		@Override
		public int compareTo(Conversation other)
		{
			int result;
			
			if ((result = this.dateStarted.compareTo(other.dateStarted)) != 0) return result;
			if ((result = this.localAccount.compareTo(other.localAccount)) != 0) return result;
			if ((result = this.remoteAccount.compareTo(other.remoteAccount)) != 0) return result;
			if (this.isConference != other.isConference) return this.isConference ? 1 : -1;
			
			return 0;
		}
		
		public boolean equals(Conversation other)
		{
			return (this.compareTo(other) == 0);
		}
		
		@Override
		public String toString()
		{
			return (this.isConference ? "Conference" : "Conversation")+
				" with "+this.localAccount+
				" using "+this.remoteAccount+
				" on "+this.dateStarted+"\n";
		}
		
		/**
		 * Adds a speaker to the conversation that already
		 * has an underlying SQL object.
		 * 
		 * @param name The name of the speaker
		 * @param account The account used by the speaker
		 * @param sqlId The SQL id of the underlying SQL object
		 * @return A reference to the newly added speaker
		 */
		public Speaker _loadSpeaker(String name, Account account, int sqlId) throws SQLException
		{
			Speaker speaker = new Speaker(name, account, sqlId);
			this._speakers.add(speaker);
			
			return speaker;
		}
		
		/**
		 * This function is used by subclasses to return
		 * a reference to their containing conversation.
		 * 
		 * @return A reference to this conversation
		 */
		protected Conversation _getConversation()
		{
			return this;
		}
		
		/**
		 * Class for modeling a reference to a speaker in the conversation.
		 */
		public class Speaker implements Comparable<Speaker>
		{
			public final String name;
			public final Account account;
			public final int sqlId;
			
			/**
			 * Constructor.
			 * 
			 * @param name The speaker's name
			 * @param account The speaker's account
			 * @param sqlId The SQL id of the speaker
			 */
			private Speaker(String name, Account account, int sqlId)
			{
				this.name = name;
				this.account = account;
				this.sqlId = sqlId;
			}
			
			/**
			 * Gets the conversation to which this speaker belongs.
			 * 
			 * @return A reference to the containing conversation
			 */
			public Conversation getConversation()
			{
				return _getConversation();
			}
			
			/**
			 * Returns this speaker's index in the conversation's
			 * speakers list. 
			 * 
			 * @return The speaker's index (0-based)
			 */
			public int getIndex()
			{
				return _getConversation()._speakers.indexOf(this);
			}

			@Override
			public int compareTo(Speaker other)
			{
				return this.name.compareTo(other.name);
			}
		}
		
		/**
		 * Class for modelling a reference to a reply.
		 */
		public class Reply
		{
			public final Date date;
			public final Speaker speaker;
			public final String text;
			public final int sqlId;
			
			/**
			 * Constructor.
			 * 
			 * @param date The date and time of the reply
			 * @param speaker A reference to the speaker making
			 *                the reply, or null for system replies
			 * @param text The content of the reply
			 * @param sqlId The SQL id for the reply
			 */
			private Reply(Date date, Speaker speaker, String text, int sqlId)
			{
				this.date = date;
				this.speaker = speaker;
				this.text = text;
				this.sqlId = sqlId;
			}
			
			/**
			 * Gets the conversation to which this reply belongs.
			 * 
			 * @return A reference to the containing conversation
			 */
			public Conversation getConversation()
			{
				return _getConversation();
			}
		}
	}
	
	/**
	 * Class for a lazy list that allows accessing the results of a
	 * conversations query while keeping only a limited number of
	 * them in memory at all times.
	 */
	public class WindowedConversationList extends AbstractList<Conversation>
	{
		protected ConversationsQuery _query;
		protected int _windowSize;
		protected List<Conversation> _window = new ArrayList<Conversation>(0);
		protected int _windowBase = 0;
		protected int _cachedConvCount = -1;
		
		public WindowedConversationList(ConversationsQuery query, int windowSize)
		{
			this._query = query;
			this._windowSize = windowSize;
		}
		
		@Override
		public Conversation get(int index)
		{
			if ((index >= this._windowBase) && (index < this._windowBase+this._window.size()))
				return this._window.get(index-this._windowBase);
			
			try
			{
				this._windowBase = index-(index % this._windowSize);
				this._window = queryConversations(this._query, this._windowBase, _windowSize);
				
				return this._window.get(index-this._windowBase);
			}
			catch (Exception e)
			{
				return null;
			}
		}

		@Override
		public int size()
		{
			if (this._cachedConvCount != -1) return this._cachedConvCount;
			
			try
			{
				this._cachedConvCount = countConversationsInQuery(this._query);
				return this._cachedConvCount;
			}
			catch (Exception e)
			{
				return 0;
			}
		}		
	}
}
