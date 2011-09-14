/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.msn_import;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.FreeAccount;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMService;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.models.import_common.Alias;
import uniarchive.models.import_common.ConfirmLocalNamesQuery;
import uniarchive.models.import_common.Feedback;
import uniarchive.models.import_common.OperationStatus;
import uniarchive.models.import_common.UnresolvedAliasesQuery;

/**
 * Class for importing a MSN Messenger archive.
 */
public class MsnImportJob
{
	protected File _archivePath;
	protected ProgressListener _progressListener;
	
	protected List<MsnConversationInfo> _conversations;
	protected Set<String> _names;
	protected Set<String> _localNames;
	protected List<Alias> _aliases;
	
	/**
	 * Constructor for a MSN import job.
	 * 
	 * @param archivePath The path to the folder containing the MSN archive
	 * @param progressListener An object that will be notified of any progress in the
	 *                         import (or NULL if this is not needed)
	 */
	public MsnImportJob(File archivePath, ProgressListener progressListener)
	{
		this._archivePath = archivePath;
		this._progressListener = progressListener;
		
		this._notifyProgress("Awaiting start command", 0, 0);
	}
	
	/**
	 * Runs the first phase of the import.
	 * 
	 * @return A Confirm Local Names query
	 * @throws Exception
	 */
	public ConfirmLocalNamesQuery runPhase1() throws Exception
	{
		this._conversations = this._loadConversations(this._scanForConversations());
		this._names = this._gatherNames();
		
		Set<String> remoteNames = new TreeSet<String>(this._names);
		Set<String> localNames = this._guessLocalNames();
		remoteNames.removeAll(localNames);
		
		return new ConfirmLocalNamesQuery(localNames, remoteNames);
	}
	
	/**
	 * Runs the second phase of the import.
	 * 
	 * @param localNames The list of local names confirmed by the user.
	 * @return If the list of local names confirmed by the user is accepted,
	 *         an Unresolved Aliases Query for the next phase of the import;
	 *         otherwise, an OperationStatus object that contains a description
	 *         the problems with the local name list confirmed by the user.
	 * @throws Exception
	 */
	public Feedback runPhase2(Collection<String> localNames) throws Exception
	{
		// Verify local names
		try
		{
			this._localNames = new TreeSet<String>(localNames);
			this._verifyLocalNames();
		}
		catch (Exception e)
		{
			OperationStatus status = new OperationStatus();
			status.errors.add(e.getMessage());
			return status;
		}
		
		// Local names OK, form the unresolved aliases query
		List<Alias> unresolved = new ArrayList<Alias>();
		for (String name : this._names) unresolved.add(new Alias(IMService.MSN, name, null));
		
		return new UnresolvedAliasesQuery(unresolved, this._guessAccounts());
	}
	
	/**
	 * Runs the third phase of the import.
	 * 
	 * @param A list of resolved aliases (these must be the same
	 *        as those given at the end of the previous phase)
	 * @return If the resolution list is accepted, the converted archive;
	 *         Otherwise, an OperationStatus object that describes the
	 *         problems encountered with the user-supplied resolution
	 *         scenario.
	 * @throws Exception
	 */
	public Object runPhase3(List<Alias> resolvedAliases) throws Exception
	{
		// Verify local names
		try
		{
			this._aliases = new ArrayList<Alias>(resolvedAliases);
			this._verifyResolvedAliases();
		}
		catch (Exception e)
		{
			OperationStatus status = new OperationStatus();
			status.errors.add(e.getMessage());
			return status;
		}
		
		// Accounts OK, create archive
		return this._convertArchive();
	}
	
	/**
	 * Notifies the progress listener (if any) of progress in
	 * the import operation.
	 * 
	 * @param comment A description of the operation in progress
	 * @param completedItems The number of completed items
	 * @param totalItems The number of items in total
	 */
	protected void _notifyProgress(String comment, int completedItems, int totalItems)
	{
		if (this._progressListener == null) return;
		
		this._progressListener.onProgress(new ProgressEvent(comment, completedItems, totalItems));
	}
	
	/**
	 * Recursively scans the archive path for conversation files.
	 * 
	 * @return A list of all conversation files found
	 */
	protected List<File> _scanForConversations() throws Exception
	{
		File file;
		List<File> convFiles = new ArrayList<File>();
		Queue<File> queue = new LinkedList<File>();
		int total = 1;
		int processed = 0;
		
		_notifyProgress("Scanning for conversations...", 0, 1);
		
		queue.add(this._archivePath);
		while ((file = queue.poll()) != null)
		{			
			// If this is a folder, add its subfolders to the queue
			if (file.isDirectory())
			{
				File[] subFiles = file.listFiles();
				if (subFiles == null) throw new RuntimeException("Could not open folder '"+file+"'");
				
				for (File subFile : subFiles) queue.add(subFile);
				total += subFiles.length;
			}
			else if (file.isFile() && file.getName().toLowerCase().endsWith(".xml"))
			{	
				// Found a conversation file, add it to the list
				convFiles.add(file);
			}
			
			processed++;			
			_notifyProgress("Scanning for conversations...", processed, total);
		}
		
		return convFiles;
	}
	
	/**
	 * Analyze conversation files and load conversations in raw
	 * format.
	 * 
	 * @param convFiles A list of conversation files to load
	 * @return A list of conversations in raw format
	 */
	protected List<MsnConversationInfo> _loadConversations(List<File> convFiles)
	{
		int processed = 0;
		
		List<MsnConversationInfo> conversations = new ArrayList<MsnConversationInfo>();
		_notifyProgress("Analyzing conversation files...", 0, convFiles.size());
		
		for (File convFile : convFiles)
		{
			conversations.addAll(MsnConversationInfo.loadFromFile(convFile));
			
			processed++;
			_notifyProgress("Analyzing conversation files...", processed, convFiles.size());
		}
		
		return conversations;
	}
	
	/**
	 * Gathers the names used in all currently loaded conversations.
	 * 
	 * @return A set of names
	 */
	protected Set<String> _gatherNames()
	{
		Set<String> names = new TreeSet<String>();
		for (MsnConversationInfo conv : this._conversations) names.addAll(conv.speakers);
		
		return names;
	}
	
	/**
	 * Analyzes loaded conversations and attempts to
	 * determine, using a heuristic, which names correspond
	 * to local accounts.
	 * 
	 * @return A set of names
	 */
	protected Set<String> _guessLocalNames()
	{
		Map<String, Set<String>> appearsWith = new TreeMap<String, Set<String>>();
		
		for (MsnConversationInfo conv : this._conversations)
		{
			if (conv.speakers.size() != 2) continue;
			
			Iterator<String> speakers = conv.speakers.iterator(); 
			String speaker1 = speakers.next();
			String speaker2 = speakers.next();
			
			if (!appearsWith.containsKey(speaker1)) appearsWith.put(speaker1, new TreeSet<String>());
			if (!appearsWith.containsKey(speaker2)) appearsWith.put(speaker2, new TreeSet<String>());
			
			appearsWith.get(speaker1).add(speaker2);
			appearsWith.get(speaker2).add(speaker1);
		}
		
		// The heuristic is as follows: names that appear with at least
		// two distinct other names in conversations might identify
		// a local account
		Set<String> localNames = new TreeSet<String>();
		for (String name : appearsWith.keySet())
			if (appearsWith.get(name).size() >= 2)
				localNames.add(name);
		
		return localNames;
	}
	
	/**
	 * Checks whether the current information regarding local names
	 * is enough to unambiguously determine the direction of each reply.
	 * 
	 * If the function detects a problem with the list, it will throw
	 * an exception describing the problem; otherwise it will do nothing.
	 */
	public void _verifyLocalNames()
	{
		for (MsnConversationInfo conv : this._conversations)
			for (String sender : conv.interactions.keySet())
				for (String receiver : conv.interactions.get(sender))
				{
					if (this._localNames.contains(sender) == this._localNames.contains(receiver))
						throw new RuntimeException("In conversation '"+conv.file+"',\n"+
								"exactly one of the names '"+sender+"', '"+receiver+"' must be local.");
				}
	}
	
	/**
	 * Checks whether the current information regarding the
	 * resolution of aliases to accounts is consistent.
	 * 
	 * If the function detects a problem with the resolution, it will throw
	 * an exception describing the problem; otherwise it will do nothing.
	 */
	public void _verifyResolvedAliases()
	{
		for (Alias alias1 : this._aliases)
			for (Alias alias2 : this._aliases)
				if ((alias1.resolution.equals(alias2.resolution)) &&
						(this._localNames.contains(alias1.name) != this._localNames.contains(alias2.name)))
				{
					throw new RuntimeException(
							"Both '"+alias1.name+"' and '"+alias2.name+"' resolve to account '"+
							alias1.resolution.toString()+"',\n"+
							"but only one is a local name.");
				}
	}
	
	/**
	 * Assembles a set of (hypothetical) accounts guessed
	 * from the conversation filename. Note that there is
	 * no guarantee that any of these are actually real.
	 * 
	 * @return A list of guessed accounts
	 */
	protected List<FreeAccount> _guessAccounts()
	{
		Set<String> accountNames = new TreeSet<String>();
		List<FreeAccount> accounts = new ArrayList<FreeAccount>();
		
		for (MsnConversationInfo conv : this._conversations)
			if (conv.accountNameGuess != null) accountNames.add(conv.accountNameGuess);
		
		for (String name : accountNames)
		{
			accounts.add(new FreeAccount(IMService.MSN, name+"@hotmail.com"));
			accounts.add(new FreeAccount(IMService.MSN, name+"@live.com"));
		}
		
		return accounts;
	}
	
	/**
	 * Executes the final archive conversion operation.
	 * All names must be identified by this point.
	 * 
	 * @return The MSN archive in internal format.
	 */
	protected IMArchive _convertArchive() throws Exception
	{
		// Create archive
		IMArchive archive = new IMArchive();
		Group defaultGroup = archive.createGroup("Default");
		
		// Create contacts, identities and accounts, and
		// establish the account to which each name resolves
		Map<String, Account> resolution = new TreeMap<String, Account>();
		for (Alias alias : this._aliases)
		{
			Account account = archive.getAccountByName(IMService.MSN, alias.resolution.name);
			
			if (account == null)
			{
				Contact contact;
				if (this._localNames.contains(alias.name))
					contact = archive.createIdentity(alias.name);
				else
					contact = defaultGroup.createContact(alias.name);
				
				account = contact.createAccount(IMService.MSN, alias.resolution.name);
			}
			
			resolution.put(alias.name, account);
		}
		
		// Convert and add conversations
		int processed = 0;
		
		_notifyProgress("Converting conversations...", 0, this._conversations.size());
		
		for (MsnConversationInfo conv : this._conversations)
		{
			this._convertConversation(archive, conv, resolution);
			
			processed++;
			_notifyProgress("Converting conversations...", processed, this._conversations.size());
		}
		
		return archive;
	}
	
	/**
	 * Converts a single MSN conversation to internal format.
	 * 
	 * @param archive The archive containing all the accounts
	 * @param msnConv A MSN conversation info object
	 * @param resolution A map of aliases and the accounts they correspond to
	 * @return A conversation object in internal format, replies included
	 */
	protected Conversation _convertConversation(IMArchive archive, MsnConversationInfo msnConv,
			Map<String, Account> resolution) throws Exception
	{
		// Determine the local and remote accounts for the conversation
		Account localAccount = null;
		Account remoteAccount = null;
		for (String speaker : msnConv.speakers)
		{
			if ((localAccount == null) && this._localNames.contains(speaker)) localAccount = resolution.get(speaker);
			if ((remoteAccount == null) && !this._localNames.contains(speaker)) remoteAccount = resolution.get(speaker);
		}
		
		// Create conversation
		Conversation conv = archive.createConversation(msnConv.dateStarted, localAccount, remoteAccount,
				msnConv.isConference);
		
		// Convert speakers
		for (String name : msnConv.speakers)
			conv.addSpeaker(name, resolution.get(name));
		
		// Convert replies
		for (RawReply rawReply : msnConv)
		{
			conv.addReply(rawReply.date, (rawReply.type == RawReply.Type.REGULAR) ?
					conv.getSpeakerByName(rawReply.sender) : null, rawReply.text);
		}
		
		return conv;
	}
}
