/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import uniarchive.models.IntList;

/**
 * Class for managing and performing operations on
 * an Sqlite database containing message archives.
 */
public class ArchiveDb
{
	protected static final String MAIN_DB_FILE = "./uniarc.sqlite";
	protected static final int MAX_IN_SIZE = 100;
	
	protected static ArchiveDb _instance;
	
	protected Connection _conn;
	protected Map<String, PreparedStatement> _statements;
	protected Map<Integer, IMService> _idToService;
	protected Map<IMService, Integer> _serviceToId;
	
	/**
	 * Constructor.
	 */
	private ArchiveDb()
	{
		try
		{
			Class.forName("org.sqlite.JDBC");
			File arcFile = new File(MAIN_DB_FILE);
			_conn = DriverManager.getConnection("jdbc:sqlite:"+arcFile.getCanonicalPath());
			
			this._initializeTables();
			this._initializeStatements();
			this._initializeServiceMaps();
			this._cleanup();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot initialize archive database:\n"+e.toString());
		}
	}
	
	/**
	 * Returns the single instance of the archive database.
	 *  
	 * @return The archive database object
	 */
	public static ArchiveDb getInstance()
	{
		if (ArchiveDb._instance == null) ArchiveDb._instance = new ArchiveDb();
		
		return ArchiveDb._instance;
	}
	
	/**
	 * Closes the connection to the database.
	 */
	public void close()
	{
		try { _conn.close(); } catch (Exception e) {}
		_conn = null;
	}
	
	/**
	 * Gets the connection to the archive database.
	 * 
	 * @return An SQL connection.
	 */
	public Connection getConnection()
	{
		return _conn;
	}
	
	/**
	 * Returns the SQL id corresponding to an IMService
	 * constant.
	 * 
	 * @param service An IMService constant
	 * @return An SQL id in the 'services' table
	 */
	public int getServiceId(IMService service)
	{
		return _serviceToId.get(service).intValue();
	}
	
	/**
	 * Returns the IMService constant corresponding to
	 * a given service ID.
	 * 
	 * @param serviceId An SQL id in the 'services' table
	 * @return An IMService constant
	 */
	public IMService getServiceById(int serviceId)
	{
		return _idToService.get(new Integer(serviceId));
	}
	
	/**
	 * Gets the names of all archives in the database.
	 * 
	 * @return A list of archive names
	 */
	public List<String> getArchives() throws SQLException
	{
		ResultSet rset = this._execQuery("getArchives");
	
		List<String> archives = new ArrayList<String>();
		while (rset.next()) archives.add(rset.getString("name"));
		rset.close();
		
		return archives;
	}
	
	/**
	 * Searches for a (non-temporary) archive with a given name.
	 * 
	 * @param name The name of the archive
	 * @return The archive ID, or -1 if it does not exist
	 */
	public int getArchiveByName(String name) throws SQLException
	{
		ResultSet rset = this._execQuery("getArchiveByName", name);
		
		return (rset.next()) ? rset.getInt(1) : -1; 
	}
	
	/**
	 * Gets the groups in an archive in a manner suitable
	 * for loading all the data in an archive when a connection
	 * is first established.
	 * 
	 * The groups are guaranteed to be returned in the order
	 * dictated by their index field.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @return A result set containing group data
	 */
	public ResultSet loadGroups(int archiveId) throws SQLException
	{
		return this._execQuery("loadGroups", archiveId);
	}
	
	/**
	 * Gets all of the contacts in an archive in a manner suitable
	 * for loading all the data in an archive when a connection
	 * is first established.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @return A result set containing contact data
	 */
	public ResultSet loadContacts(int archiveId) throws SQLException
	{
		return this._execQuery("loadContacts", archiveId);
	}
	
	/**
	 * Gets all of the accounts in an archive in a manner suitable
	 * for loading all the data in an archive when a connection
	 * is first established.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @return A result set containing account data
	 */
	public ResultSet loadAccounts(int archiveId) throws SQLException
	{
		return this._execQuery("loadAccounts", archiveId);
	}
	
	/**
	 * Executes a query in the archive's conversation list and
	 * returns a window in the results.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @param filterLocalAccounts If non-empty, only conversations featuring
	 *                            a local account ID in this list will be returned.
	 * @param filterRemoteAccounts Ditto, for remote account IDs
	 * @param filterConversations If non-empty, only conversations with these IDs
	 *                            will be returned
	 * @param sortKeys An array of column names specifying how the result set
	 *                 should be ordered (it is first sorted by the first
	 *                 column specified, then the second, etc.)
	 * @param offset The offset of the window (0-based)
	 * @param limit The size of the window
	 * @return A result set containing conversation data in 
	 *         the specified window
	 */
	public ResultSet getConversations(int archiveId, int[] filterLocalAccounts, int[] filterRemoteAccounts,
			int[] filterConversations, String[] sortKeys, int offset, int limit) throws SQLException
	{
		StringBuilder buf = new StringBuilder();
		
		buf.append("SELECT v.id AS id, date_started, local_account_id, remote_account_id, is_conference ");
		buf.append(this._getConversationsQuery(archiveId, filterLocalAccounts, filterRemoteAccounts, filterConversations));
		for (int i=0; i<sortKeys.length; i++)
		{
			buf.append((i==0) ? " ORDER BY " : ", ");
			buf.append(sortKeys[i]);
		}
		buf.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);
		
		return _conn.createStatement().executeQuery(buf.toString());
	}
	
	/**
	 * Counts the number of conversations returned by a query.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @param filterLocalAccounts If non-empty, only conversations featuring
	 *                            a local account ID in this list will be returned.
	 * @param filterRemoteAccounts Ditto, for remote account IDs
	 * @param filterConversations If non-empty, only conversations with these IDs
	 *                            will be returned
	 * @return The number of items in the query result
	 */
	public int countConversations(int archiveId, int[] filterLocalAccounts, int[] filterRemoteAccounts,
			int[] filterConversations) throws SQLException
	{
		ResultSet rset = _conn.createStatement().executeQuery(
				"SELECT COUNT(*) "+
				this._getConversationsQuery(archiveId, filterLocalAccounts, filterRemoteAccounts, filterConversations)		
		);
		rset.next();
		
		return rset.getInt(1);
	}
	
	/**
	 * Gets the speakers associated with a number of conversations.
	 * 
	 * @param conversationIds An array of conversation IDs for which
	 *                        the speakers will be retrieved.
	 * @return A result set containing speaker data
	 */
	public ResultSet getSpeakers(int[] conversationIds) throws SQLException
	{
		return _conn.createStatement().executeQuery(
				"SELECT id, name, account_id, conversation_id"+
				" FROM speakers WHERE conversation_id IN ("+this._implodeIds(conversationIds)+")"
		);
	}
	
	/**
	 * Gets the replies in a conversation.
	 * 
	 * @param conversationId The SQL ID of the conversation
	 * @return A result set containing reply data
	 */
	public ResultSet getReplies(int conversationId) throws SQLException
	{
		return this._execQuery("getReplies", conversationId);
	}
	
	/**
	 * Counts the groups in an archive.
	 * 
	 * @param archiveId The ID of the archive
	 * @return The number of groups in the archive
	 */
	public int countGroups(int archiveId) throws SQLException
	{
		return this._execSingleNoQuery("countGroups", archiveId);
	}
	
	/**
	 * Counts the conversations in an archive.
	 * 
	 * @param archiveId The ID of the archive
	 * @return The number of conversations in the archive
	 */
	public int countConversations(int archiveId) throws SQLException
	{
		return this._execSingleNoQuery("countConversations", archiveId);
	}
	
	/**
	 * Counts the replies in a conversation.
	 * 
	 * @param conversationId The ID of the conversation
	 * @return The number of replies in the conversation
	 */
	public int countReplies(int conversationId) throws SQLException
	{
		return this._execSingleNoQuery("countReplies", conversationId);
	}
	
	/**
	 * Counts the number of conversations that depend
	 * upon any in a number of contact accounts.
	 * 
	 * @param accountIds An array containing account IDs
	 */
	public int countDependentConversations(int[] accountIds) throws SQLException
	{
		ResultSet rset = _conn.createStatement().executeQuery(
				"SELECT COUNT(*)"+
				" FROM conversations AS c"+
				" WHERE (c.remote_account_id IN ("+this._implodeIds(accountIds)+"))"+
				" OR (c.local_account_id IN ("+this._implodeIds(accountIds)+"))"+
				" OR EXISTS("+
				"  SELECT * FROM speakers AS s"+
				"  WHERE"+
				"  (s.conversation_id = c.id)"+
				"  AND (s.account_id IN ("+this._implodeIds(accountIds)+"))"+
				" )"
		);
		
		rset.next();
		return rset.getInt(1);
	}
	
	/**
	 * Creates an archive with a given name.
	 * 
	 * Note: the Identities group is not automatically created. In
	 * order for most operations to work correctly, the appropriate
	 * createGroup function should be called soon after this has
	 * finished executing.
	 * 
	 * @param name The name of the archive (null to create
	 *             a temporary archive)
	 * @return The new archive's ID
	 */
	public int createArchive(String name) throws SQLException
	{
		return this._execUpdate("createArchive", name, (name==null) ? 1 : 0);
	}
	
	/**
	 * Creates a group in a given archive.
	 * 
	 * @param archiveId The ID of the containing archive
	 * @param name The name of the group
	 * @return The new group's ID
	 */
	public int createGroup(int archiveId, String name) throws SQLException
	{
		return this._execUpdate("createGroup", archiveId, this.countGroups(archiveId), name);
	}
	
	/**
	 * Creates a contact in a given group.
	 * 
	 * @param groupId The ID of the containing group
	 * @param name The name of the contact
	 * @return The new contact's ID
	 */
	public int createContact(int groupId, String name) throws SQLException
	{
		return this._execUpdate("createContact", groupId, name);
	}
	
	/**
	 * Creates an account in a given contact.
	 * 
	 * @param contactId The ID of the containing contact
	 * @param serviceId The service ID for the account
	 * @param name The name of the account
	 * @return The new account's ID
	 */
	public int createAccount(int contactId, int serviceId, String name) throws SQLException
	{
		return this._execUpdate("createAccount", contactId, serviceId, name);
	}
	
	/**
	 * Creates a conversation in an archive.
	 *
	 * @param archiveId The ID of the containing archive
	 * @param dateStarted The starting date for the conversation
	 * @param localAccountId The ID of the local account in the conversation
	 * @param remoteAccountId The ID of the remote account in the conversation
	 * @param isConference True if the conversation is a conference, false otherwise
	 * @return The new conversation's ID
	 */
	public int createConversation(int archiveId, Date dateStarted, int localAccountId, int remoteAccountId, boolean isConference) throws SQLException
	{
		return this._execUpdate("createConversation", archiveId, dateStarted, localAccountId, remoteAccountId, isConference ? 1 : 0);
	}
	
	/**
	 * Creates a speaker in a conversation.
	 * 
	 * @param conversationId The ID of the containing conversation
	 * @param name The name of the speaker
	 * @param accountId The ID of the speaker's account
	 * @return The new speaker's ID
	 */
	public int createSpeaker(int conversationId, String name, int accountId) throws SQLException
	{
		return this._execUpdate("createSpeaker", conversationId, name, accountId);
	}
	
	/**
	 * Creates a reply in a conversation.
	 * 
	 * @param conversationId The ID of the containing conversation
	 * @param index The index of the reply in the conversation
	 * @param replyDate The date of the reply
	 * @param speakerId The ID of the speaker (or -1 for system replies)
	 * @param content The text content of the reply
	 * @return The new reply's ID
	 */
	public int createReply(int conversationId, int index, Date replyDate, int speakerId, String content) throws SQLException
	{
		if (speakerId != -1)
			return this._execUpdate("createReply", conversationId, index, replyDate, speakerId, content);
		return this._execUpdate("createReply", conversationId, index, replyDate, null, content);
	}
	
	/**
	 * Moves a group to a new position in its archive's list.
	 * 
	 * @param groupId The ID of the group
	 * @param newIndex The new index in the group list
	 */
	public void moveGroup(int groupId, int newIndex) throws SQLException
	{
		ResultSet rset = this._execQuery("getGroup", groupId);
		
		rset.next();
		int oldIndex = rset.getInt("idx");
		int archiveId = rset.getInt("archive_id");
		rset.close();
		
		this._execUpdate("moveGroup", archiveId, oldIndex, newIndex);
	}
	
	/**
	 * Merges a group into another.
	 * 
	 * @param srcGroupId The ID of the group to merge
	 * @param destGroupId The ID of the receiving group
	 */
	public void mergeGroup(int srcGroupId, int destGroupId) throws SQLException
	{
		this._execUpdate("mergeGroup", srcGroupId, destGroupId);
	}
	
	/**
	 * Merges a contact into another.
	 * 
	 * @param srcContactId The ID of the contact to merge
	 * @param destContactId The ID of the receiving contact
	 */
	public void mergeContact(int srcContactId, int destContactId) throws SQLException
	{
		this._execUpdate("mergeContact", srcContactId, destContactId);
	}
	
	/**
	 * Renames a group.
	 * 
	 * @param groupId The ID of the group
	 * @param newName The new name for the group
	 */
	public void renameGroup(int groupId, String newName) throws SQLException
	{
		this._execUpdate("renameGroup", groupId, newName);
	}
	
	/**
	 * Renames a contact.
	 * 
	 * @param contactId The ID of the contact
	 * @param newName The new name for the contact
	 */
	public void renameContact(int contactId, String newName) throws SQLException
	{
		this._execUpdate("renameContact", contactId, newName);
	}
	
	/**
	 * Renames an account.
	 * 
	 * @param accountId The ID of the account
	 * @param newName The new name for the account
	 */
	public void renameAccount(int accountId, String newName) throws SQLException
	{
		this._execUpdate("renameAccount", accountId, newName);
	}
	
	/**
	 * Deletes a series of groups as well as all dependent
	 * contacts, accounts and conversations.
	 * 
	 * @param groupIds An array containing the IDs of the
	 *                 groups to be deleted
	 */
	public void deleteGroups(int[] groupIds) throws SQLException
	{
		if (groupIds.length == 0) return;
		
		Statement stat = _conn.createStatement();
		ResultSet rset = stat.executeQuery("SELECT id FROM accounts WHERE (contact_id IN (SELECT id FROM contacts WHERE group_id IN ("+this._implodeIds(groupIds)+")))");
		
		IntList ids = new IntList();
		while (rset.next()) ids.add(rset.getInt(1));
		rset.close();
		
		// Delete underlying data
		this.deleteAccounts(ids.toArray());
		
		// Retrieve the archive ID and delete the groups
		int archiveId = this._execSingleNoQuery("getGroupArchive", groupIds[0]);
		stat.executeUpdate("DELETE FROM groups WHERE id IN ("+this._implodeIds(groupIds)+")");
		
		// Now reindex the groups
		StringBuilder query1 = new StringBuilder("UPDATE groups SET idx = CASE");
		StringBuilder query2 = new StringBuilder(" END WHERE id IN (");
		
		rset = this._execQuery("loadGroups", archiveId);
		int newIndex = 0;
		while (rset.next())
		{
			int groupId = rset.getInt("id");
			query1.append(" WHEN id=").append(groupId).append(" THEN ").append(newIndex);
			if (newIndex > 0) query2.append(",");
			query2.append(groupId);
			newIndex++;
		}
		rset.close();
		
		query1.append(query2).append(")");
		
		if (newIndex>0) stat.executeUpdate(query1.toString());
	}
	
	/**
	 * Deletes a series of contacts as well as all dependent
	 * accounts and conversations.
	 * 
	 * @param contactIds An array containing the IDs of the
	 *                   contacts to be deleted
	 */
	public void deleteContacts(int[] contactIds) throws SQLException
	{
		Statement stat = _conn.createStatement();
		ResultSet rset = stat.executeQuery("SELECT id FROM accounts WHERE (contact_id IN ("+this._implodeIds(contactIds)+"))");
		
		IntList ids = new IntList();
		while (rset.next()) ids.add(rset.getInt(1));
		rset.close();
		
		this.deleteAccounts(ids.toArray());
		
		stat.executeUpdate("DELETE FROM contacts WHERE id IN ("+this._implodeIds(contactIds)+")");
	}
	
	/**
	 * Deletes a series of accounts from their respective
	 * contacts, as well as all dependent conversations.
	 * 
	 * @param accountIds An array containing the IDs of the
	 *                   accounts to be deleted
	 */
	public void deleteAccounts(int[] accountIds) throws SQLException
	{
		String inClause = "IN ("+this._implodeIds(accountIds)+")";
		
		Statement stat = _conn.createStatement();
		stat.executeUpdate("DELETE FROM accounts WHERE id "+inClause);
		
		// Delete conversations depending on these accounts
		stat.executeUpdate(
				"DELETE FROM conversations"+
				" WHERE (remote_account_id "+inClause+")"+
				" OR (local_account_id "+inClause+")"+
				" OR EXISTS("+
				"  SELECT * FROM speakers AS s"+
				"  WHERE"+
				"  (conversation_id = conversations.id)"+
				"  AND (account_id "+inClause+")"+
				" )"
		);
	}
	
	/**
	 * Deletes an archive.
	 * 
	 * @param archiveId The ID of the archive to be deleted
	 */
	public void deleteArchive(int archiveId) throws SQLException
	{
		this.zapArchiveData(archiveId);
		this._execUpdate("deleteArchive", archiveId);
	}
	
	/**
	 * Deletes all data in an archive.
	 * 
	 * @param archiveId The ID of the archive whose data is to
	 *                  be deleted.
	 */
	public void zapArchiveData(int archiveId) throws SQLException
	{
		this._execUpdate("deleteArchiveReplies", archiveId);
		this._execUpdate("deleteArchiveSpeakers", archiveId);
		this._execUpdate("deleteArchiveConversations", archiveId);
		this._execUpdate("deleteArchiveAccounts", archiveId);
		this._execUpdate("deleteArchiveContacts", archiveId);
		this._execUpdate("deleteArchiveGroups", archiveId);
	}
	
	/**
	 * Deletes a set of conversations in an archive.
	 * 
	 * @param conversationIds An array containing the IDs of the conversations
	 *                        to be deleted
	 */
	public void deleteConversations(int[] conversationIds) throws SQLException
	{
		if (conversationIds.length > MAX_IN_SIZE)
		{
			int blocks = conversationIds.length/MAX_IN_SIZE;
			for (int i=0; i<blocks; i++)
				this.deleteConversations(Arrays.copyOfRange(conversationIds, MAX_IN_SIZE*i, MAX_IN_SIZE*(i+1)));
			
			this.deleteConversations(Arrays.copyOfRange(conversationIds, blocks*MAX_IN_SIZE, conversationIds.length));
			return;
		}
		
		Statement stat = _conn.createStatement();
		stat.executeUpdate("DELETE FROM replies WHERE conversation_id IN ("+this._implodeIds(conversationIds)+")");
		stat.executeUpdate("DELETE FROM speakers WHERE conversation_id IN ("+this._implodeIds(conversationIds)+")");
		stat.executeUpdate("DELETE FROM conversations WHERE id IN ("+this._implodeIds(conversationIds)+")");
	}
	
	/**
	 * Sets the group to which a contact belongs.
	 * 
	 * @param contactId The ID of the contact
	 * @param groupId The ID of the new parent group
	 */
	public void setContactGroup(int contactId, int groupId) throws SQLException
	{
		this._execUpdate("setContactGroup", contactId, groupId);
	}
	
	/**
	 * Sets the contact to which an account belongs.
	 * 
	 * @param accountId The ID of the account
	 * @param contactId The ID of the new parent contact
	 */
	public void setAccountContact(int accountId, int contactId) throws SQLException
	{
		this._execUpdate("setAccountContact", accountId, contactId);
	}
	
	/**
	 * Gets a database-scoped unique ID (for use in distinguishing
	 * concurrent operations)
	 * 
	 * @return A unique ID
	 */
	protected int _getUniqueId() throws SQLException
	{
		return this._execUpdate("createUid");
	}
	
	/**
	 * Frees a previously obtained unique ID.
	 * 
	 * @param uid The unique ID to release
	 */
	protected void _releaseUniqueId(int uid) throws SQLException
	{
		this._execUpdate("deleteUid", uid);
	}
	
	/**
	 * Gets the FROM and WHERE clauses for a conversations query
	 * used in the getConversations() and countConversations() functions. 
	 * 
	 * @param archiveId The ID of the containing archive
	 * @param filterLocalAccounts See getConversations() for details
	 * @param filterRemoteAccounts See getConversations() for details
	 * @param filterConversations See getConversations() for details
	 * @return An SQL string for the query (without fields, ORDER BY and
	 *         LIMIT specifiers)
	 */
	protected String _getConversationsQuery(int archiveId, int[] filterLocalAccounts, int[] filterRemoteAccounts,
			int[] filterConversations)
	{
		boolean hasLocal = (filterLocalAccounts.length > 0);
		boolean hasRemote = (filterRemoteAccounts.length > 0);
		boolean hasConv = (filterConversations.length > 0);
		
		return
			"FROM conversations AS v"+
			" INNER JOIN accounts AS a ON (a.id=v.remote_account_id)"+
			" INNER JOIN contacts AS c ON (c.id=a.contact_id)"+
			" WHERE "+
			(hasConv ? ("v.id IN ("+_implodeIds(filterConversations)+")") : "")+
			((hasLocal && hasConv) ? " AND " : "")+
			(hasLocal ? ("local_account_id IN ("+_implodeIds(filterLocalAccounts)+")") : "")+
			((hasRemote && (hasLocal || hasConv)) ? " AND " : "")+
			(hasRemote ? ("remote_account_id IN ("+_implodeIds(filterRemoteAccounts)+")") : "")+
			((!hasLocal && !hasRemote && !hasConv) ? ("v.archive_id="+archiveId) : "");
	}
	
	/**
	 * Formats a comma-separated string from an array of IDs.
	 * 
	 * @param ids An array of IDs
	 * @return A string containing the IDs separated by commas
	 */
	protected String _implodeIds(int[] ids)
	{
		StringBuilder buf = new StringBuilder();
		
		for (int i=0; i<ids.length; i++)
		{
			if (i>0) buf.append(",");
			buf.append(ids[i]);
		}
		
		return buf.toString();
	}
	
	/**
	 * Checks whether all the necessary tables exist and
	 * creates them if the do not.
	 */
	protected void _initializeTables() throws SQLException
	{
		// Note: the following columns are technically redundant, but
		// required for performance reasons:
		//
		// - conversations.archive_id
		// - replies.conversation_id
		final String[][] INIT_TABLES_DATA = {
			new String[] { "archives", "name TEXT UNIQUE, is_temp INTEGER NOT NULL" },
			new String[] { "groups", "idx INTEGER NOT NULL, name TEXT NOT NULL, archive_id INTEGER NOT NULL" },
			new String[] { "contacts", "name TEXT NOT NULL, group_id INTEGER NOT NULL" },
			new String[] { "accounts", "service INTEGER NOT NULL, name TEXT NOT NULL, contact_id INTEGER NOT NULL" },
			new String[] { "services", "name TEXT UNIQUE NOT NULL, shortName TEXT UNIQUE NOT NULL" },
			new String[] { "conversations", "date_started DATETIME NOT NULL, local_account_id INTEGER NOT NULL, remote_account_id INTEGER NOT NULL, is_conference INTEGER NOT NULL, archive_id NOT NULL" },
			new String[] { "speakers", "name TEXT NOT NULL, account_id INTEGER NOT NULL, conversation_id INTEGER NOT NULL" },
			new String[] { "replies", "idx INTEGER NOT NULL, reply_date DATETIME NOT NULL, speaker_id INTEGER, content TEXT, conversation_id INTEGER NOT NULL" },
			// Temporary tables
			new String[] { "tmp_unique_ids", "" }
		};
		
		final String[][] INIT_INDEXES_DATA = {
			new String[] { "groups_archive", "groups(archive_id)" },
			new String[] { "contacts_group", "contacts(group_id)" },
			new String[] { "accounts_contact", "accounts(contact_id)" },
			new String[] { "conversations_archive", "conversations(archive_id)" },
			new String[] { "conversations_archive_date", "conversations(archive_id,date_started)" },
			new String[] { "conversations_archive_local", "conversations(archive_id,local_account_id)" },
			new String[] { "conversations_archive_remote", "conversations(archive_id,remote_account_id)" },
			new String[] { "speakers_conversation", "speakers(conversation_id)" },
			new String[] { "speakers_account", "speakers(account_id)" },
			new String[] { "replies_conversation", "replies(conversation_id)" }
		};
		
		// Check what tables and indexes exist
		
		Set<String> tables = new TreeSet<String>();
		Set<String> indexes = new TreeSet<String>();

		Statement stat = _conn.createStatement();
		ResultSet rset = stat.executeQuery("SELECT type, name FROM sqlite_master WHERE type=\"table\" OR type=\"index\"");
		
		while (rset.next())
		{
			if (rset.getString("type").equalsIgnoreCase("table"))
				tables.add(rset.getString("name").toLowerCase());
			else
				indexes.add(rset.getString("name").toLowerCase());
		}
		rset.close();
		
		// Create the tables that do not exist yet
		for (String[] tabSpec : INIT_TABLES_DATA)
		{
			if (tables.contains(tabSpec[0])) continue;
			
			stat.executeUpdate(
					"CREATE "+
					(tabSpec[0].startsWith("tmp_") ? "TEMPORARY " : "")+
					"TABLE "+tabSpec[0]+"(id INTEGER PRIMARY KEY"+
					(tabSpec[1].isEmpty() ? "" : ", ")+
					tabSpec[1]+
					")");
		}
		
		// Create the indexes that do not exist yet
		for (String[] idxSpec : INIT_INDEXES_DATA)
		{
			if (indexes.contains(idxSpec[0])) continue;
			stat.executeUpdate("CREATE INDEX "+idxSpec[0]+" ON "+idxSpec[1]);
		}
	}
	
	/**
	 * Initialize the prepared statements system.
	 */
	protected void _initializeStatements() throws SQLException
	{
		final String[][] INIT_DATA = {
			new String[] { "getArchives", "SELECT name FROM archives WHERE is_temp=0" },
			new String[] { "getServices", "SELECT id, shortName FROM services" },
			new String[] { "getArchiveByName", "SELECT id FROM archives WHERE name=?1 AND is_temp=0" },
			new String[] { "getReplies",
					"SELECT id, idx, reply_date, speaker_id, content "+
					"FROM replies WHERE conversation_id=?1 "+
					"ORDER BY idx"
				},
			new String[] { "getGroup", "SELECT idx, name, archive_id FROM groups WHERE id=?1" },
			new String[] { "getGroupArchive", "SELECT archive_id FROM groups WHERE id=?1" },
			new String[] { "loadGroups", "SELECT id, idx, name FROM groups WHERE archive_id=?1 ORDER BY idx" },
			new String[] { "loadContacts",
					"SELECT c.id AS id, c.name AS name, c.group_id AS group_id "+
					"FROM contacts AS c INNER JOIN groups AS g ON g.id=c.group_id "+
					"WHERE g.archive_id=?1 "
					},
			new String[] { "loadAccounts",
					"SELECT a.id AS id, a.service AS service_id, a.name AS name, a.contact_id AS contact_id "+
					"FROM accounts AS A INNER JOIN contacts AS c ON c.id=a.contact_id INNER JOIN groups AS g ON g.id=c.group_id "+
					"WHERE g.archive_id=?1 "
					},
			new String[] { "createUid", "INSERT INTO tmp_unique_ids DEFAULT VALUES" },
			new String[] { "deleteUid", "DELETE FROM tmp_unique_ids WHERE id=?1" },
			new String[] { "createService", "INSERT INTO services(name,shortName) VALUES (?1,?2)" },
			new String[] { "createArchive", "INSERT INTO archives(name,is_temp) VALUES (?1,?2)" },
			new String[] { "createGroup", "INSERT INTO groups(archive_id,idx,name) VALUES (?1,?2,?3)" },
			new String[] { "createContact", "INSERT INTO contacts(group_id,name) VALUES (?1,?2)" },
			new String[] { "createAccount", "INSERT INTO accounts(contact_id,service,name) VALUES (?1,?2,?3)" },
			new String[] { "createConversation", "INSERT INTO conversations(archive_id,date_started,local_account_id,remote_account_id,is_conference) VALUES (?1,?2,?3,?4,?5)" },
			new String[] { "createSpeaker", "INSERT INTO speakers(conversation_id,name,account_id) VALUES (?1,?2,?3)" },
			new String[] { "createReply", "INSERT INTO replies(conversation_id,idx,reply_date,speaker_id,content) VALUES (?1,?2,?3,?4,?5)" },
			new String[] { "moveGroup",
					"UPDATE groups "+
					"SET idx = CASE WHEN idx=?2 THEN ?3 ELSE (CASE WHEN ?2>?3 THEN idx+1 ELSE idx-1 END) END "+
					"WHERE (archive_id=?1) AND (idx BETWEEN MIN(?2,?3) AND MAX(?2,?3))"
					},
			new String[] { "mergeGroup", "UPDATE contacts SET group_id=?2 WHERE group_id=?1" },
			new String[] { "mergeContact", "UPDATE accounts SET contact_id=?2 WHERE contact_id=?1" },
			new String[] { "renameGroup", "UPDATE groups SET name=?2 WHERE id=?1" },
			new String[] { "renameContact", "UPDATE contacts SET name=?2 WHERE id=?1" },
			new String[] { "renameAccount", "UPDATE accounts SET name=?2 WHERE id=?1" },
			new String[] { "countGroups", "SELECT COUNT(*) FROM groups WHERE archive_id=?1" },
			new String[] { "countConversations", "SELECT COUNT(*) FROM conversations WHERE archive_id=?1" },
			new String[] { "countReplies", "SELECT COUNT(*) FROM replies WHERE conversation_id=?1" },
			new String[] { "setContactGroup", "UPDATE contacts SET group_id=?2 WHERE id=?1" },
			new String[] { "setAccountContact", "UPDATE accounts SET contact_id=?2 WHERE id=?1" },
			new String[] { "cleanupArchives", "DELETE FROM archives WHERE is_temp=1" },
			new String[] { "cleanupGroups", "DELETE FROM groups WHERE archive_id NOT IN (SELECT id FROM archives)" },
			new String[] { "cleanupConversations", "DELETE FROM conversations WHERE archive_id NOT IN (SELECT id FROM archives)" },
			new String[] { "cleanupContacts", "DELETE FROM contacts WHERE group_id NOT IN (SELECT id FROM groups)" },
			new String[] { "cleanupAccounts", "DELETE FROM accounts WHERE contact_id NOT IN (SELECT id FROM contacts)" },
			new String[] { "cleanupSpeakers", "DELETE FROM speakers WHERE conversation_id NOT IN (SELECT id FROM conversations)" },
			new String[] { "cleanupReplies", "DELETE FROM replies WHERE conversation_id NOT IN (SELECT id FROM conversations)" },
			new String[] { "deleteArchive", "DELETE FROM archives WHERE id=?1" },
			new String[] { "deleteArchiveReplies", "DELETE FROM replies WHERE conversation_id IN (SELECT id FROM conversations WHERE archive_id=?1)" },
			new String[] { "deleteArchiveSpeakers", "DELETE FROM speakers WHERE conversation_id IN (SELECT id FROM conversations WHERE archive_id=?1)" },
			new String[] { "deleteArchiveContacts", "DELETE FROM contacts WHERE group_id IN (SELECT id FROM groups WHERE archive_id=?1)" },
			new String[] { "deleteArchiveAccounts", "DELETE FROM accounts WHERE contact_id IN (SELECT id FROM contacts WHERE group_id IN (SELECT id FROM groups WHERE archive_id=?1))" },
			new String[] { "deleteArchiveConversations", "DELETE FROM conversations WHERE archive_id=?1" },
			new String[] { "deleteArchiveGroups", "DELETE FROM groups WHERE archive_id=?1" }
		};
		
		_statements = new TreeMap<String, PreparedStatement>();
		for (String[] statSpec : INIT_DATA)
			_statements.put(statSpec[0], _conn.prepareStatement(statSpec[1]));
	}
	
	/**
	 * Initializes maps that connect service IDs in the database to
	 * IMService constants in the program.
	 * 
	 * Note that if the program knows some services that aren't in
	 * the database, these will be added.
	 */
	protected void _initializeServiceMaps() throws SQLException
	{
		_idToService = new TreeMap<Integer, IMService>();
		_serviceToId = new TreeMap<IMService, Integer>();
		
		ResultSet rset = this._execQuery("getServices");
		while (rset.next())
		{
			Integer serviceId = rset.getInt("id");
			IMService service = IMService.fromShortName(rset.getString("shortName"));
			if (service == null) service = IMService.GENERIC;
			
			_idToService.put(serviceId, service);
			_serviceToId.put(service, serviceId);
		}
		
		for (IMService service : IMService.values())
		{
			if (_serviceToId.containsKey(service)) continue;
			
			Integer serviceId = this._execUpdate("createService", service.name(), service.shortName);
			
			_idToService.put(serviceId, service);
			_serviceToId.put(service, serviceId);
		}
	}
	
	/**
	 * Deletes all temporary data in the archive.
	 * 
	 * WARNING: you must not call this while there are still
	 * unnamed IMArchive objects open.
	 */
	protected void _cleanup() throws SQLException
	{
		this._execUpdate("cleanupArchives");
		this._execUpdate("cleanupGroups");
		this._execUpdate("cleanupContacts");
		this._execUpdate("cleanupAccounts");
		this._execUpdate("cleanupConversations");
		this._execUpdate("cleanupSpeakers");
		this._execUpdate("cleanupReplies");
	}
	
	/**
	 * Executes a prepared query statement.
	 * 
	 * @param statementName The name of the statement to execute
	 * @param parameters A variable number of parameters for the
	 *                   statement
	 * @return A result set
	 */
	protected ResultSet _execQuery(String statementName, Object... parameters) throws SQLException
	{
		PreparedStatement pstat = _statements.get(statementName);
		for (int i=0; i<parameters.length; i++) pstat.setObject(i+1, parameters[i]);
		return pstat.executeQuery();
	}
	
	/**
	 * Executes a prepared query statement that returns a single
	 * integer.
	 * 
	 * @param statementName The name of the statement to execute
	 * @param parameters A variable number of parameters for the
	 *                   statement
	 * @return The result of the query
	 */
	protected int _execSingleNoQuery(String statementName, Object... parameters) throws SQLException
	{
		PreparedStatement pstat = _statements.get(statementName);
		for (int i=0; i<parameters.length; i++) pstat.setObject(i+1, parameters[i]);
		ResultSet rset = pstat.executeQuery();
		
		rset.next();
		return rset.getInt(1);
	}
	
	/**
	 * Executes a prepared update statement and retrieves
	 * the generated key.
	 * 
	 * @param statementName The name of the statement to execute
	 * @param parameters A variable number of parameters for the
	 *                   statement
	 * @return A single generated id, or -1 if there was none
	 */
	protected int _execUpdate(String statementName, Object... parameters) throws SQLException
	{
		PreparedStatement pstat = _statements.get(statementName);
		for (int i=0; i<parameters.length; i++) pstat.setObject(i+1, parameters[i]);
		pstat.executeUpdate();
		
		ResultSet rset = pstat.getGeneratedKeys();
		if (rset.next()) return rset.getInt(1);
		
		return -1;
	}
}
