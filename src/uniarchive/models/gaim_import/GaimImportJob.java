/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import uniarchive.models.NameIndex;
import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.FreeAccount;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.models.import_common.Alias;
import uniarchive.models.import_common.ConfirmLocalNamesQuery;
import uniarchive.models.import_common.UnresolvedAliasesQuery;

/**
 * Class for importing a GAIM/Pidgin archive.
 */
public class GaimImportJob
{
	protected File _archivePath;
	protected ProgressListener _progressListener;
	
	protected List<GaimConversationInfo> _conversations;
	protected NameIndex<ImportedAccountInfo> _accounts;
	protected Set<String> _localNames;
	
	/**
	 * Constructor for a GAIM import job.
	 * 
	 * @param archivePath The path to the folder containing the Gaim archive
	 * @param progressListener An object that will be notified of any progress in the
	 *                         import (or NULL if this is not needed)
	 */
	public GaimImportJob(File archivePath, ProgressListener progressListener)
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
		this._loadConversations(this._scanForConversations());
		Set<String> remoteNames = this._gatherNames();
		Set<String> localNames = this._guessLocalNames();
		remoteNames.removeAll(localNames);
		
		return new ConfirmLocalNamesQuery(localNames, remoteNames);
	}
	
	/**
	 * Runs the second phase of the import.
	 * 
	 * @param localNames The list of local names confirmed by the
	 *                   user.
	 * @return A Confirm Accounts query
	 * @throws Exception
	 */
	public ConfirmAccountsQuery runPhase2(Collection<String> localNames) throws Exception
	{
		this._localNames = new TreeSet<String>(localNames);
		
		this._gatherAccountInfo();
		
		return new ConfirmAccountsQuery(this._accounts.getAllItems());
	}
	
	/**
	 * Runs the third phase of the import.
	 * 
	 * @return An Unresolved Aliases query
	 * @throws Exception
	 */
	public UnresolvedAliasesQuery runPhase3() throws Exception
	{
		List<Alias> unresolved = this._identifySpeakers();
		List<FreeAccount> accounts = new ArrayList<FreeAccount>();
		for (ImportedAccountInfo info : this._accounts.getAllItems())
			accounts.add(info.account);
		
		return new UnresolvedAliasesQuery(unresolved, accounts);
	}
	
	/**
	 * Runs the fourth phase of the import.
	 * 
	 * @param A list of resolved aliases (these must be the same
	 *        as those given at the end of the previous phase)
	 * @return The converted archive 
	 * @throws Exception
	 */
	public IMArchive runPhase4(List<Alias> resolvedAliases) throws Exception
	{
		this._finalizeSpeakerIdentification(resolvedAliases);
		this._mergeResolvedAliasAccounts(resolvedAliases);
		
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
			else if (file.isFile())
			{
				// Found a conversation file, add it to the list
				String fileName = file.getName().toLowerCase();
				if (fileName.endsWith(".txt") || fileName.endsWith(".html") || fileName.endsWith(".htm"))
					convFiles.add(file);
			}
			
			processed++;			
			_notifyProgress("Scanning for conversations...", processed, total);
		}
		
		return convFiles;
	}
	
	/**
	 * Analyze conversation files and load conversation info
	 * into the conversations array.
	 * 
	 * @param convFiles A list of conversation files to load
	 */
	protected void _loadConversations(List<File> convFiles)
	{
		int processed = 0;
		
		this._conversations = new ArrayList<GaimConversationInfo>();
		_notifyProgress("Analyzing conversation files...", 0, convFiles.size());
		
		for (File convFile : convFiles)
		{
			this._conversations.addAll(GaimConversationInfo.loadFromFile(convFile));
			
			processed++;
			_notifyProgress("Analyzing conversation files...", processed, convFiles.size());
		}
	}
	
	/**
	 * Gathers the names used in all currently loaded conversations.
	 * Names that correspond to account names are omitted.
	 * 
	 * @return A set of names
	 */
	protected Set<String> _gatherNames()
	{
		Set<String> names = new TreeSet<String>();
		
		for (GaimConversationInfo conv : this._conversations)
		{
			for (String name : conv.speakers.keySet())
				if (!name.equals(conv.localAccountName) && !name.equals(conv.remoteAccountName))
					names.add(name);
		}
		
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
		Set<String> localNames = new TreeSet<String>();
		
		// Each service is treated separately
		for (List<GaimConversationInfo> convsInService : GaimConversationInfo.groupByService(this._conversations).values())
		{
			Map<String, Integer> freqTable = new TreeMap<String, Integer>();
			
			// Group conversations by the destination account
			for (List<GaimConversationInfo> convsInContext : GaimConversationInfo.groupByRemoteAccount(convsInService).values())
			{
				// Collect names used in this context
				Set<String> names = new TreeSet<String>();
				
				for (GaimConversationInfo conv : convsInContext)
				{
					if (conv.isConference) break;
					for (String name : conv.speakers.keySet())
						if (!name.equals(conv.localAccountName))
							names.add(name);
				}
				
				// Add them to the frequency table
				for (String name : names)
				{
					int currCount = freqTable.containsKey(name) ? freqTable.get(name).intValue() : 0;
					freqTable.put(name, new Integer(currCount+1));
				}
			}
			
			// Names that appear in at least two conversations with two
			// different accounts are considered local names. Note that this
			// may be wrong, since the same would happen if one of our remote
			// contacts had two accounts with the same screen name. Additionally,
			// this may miss the local screen name for services which only feature
			// one remote account. Hence, the results of this heuristic require
			// supervision and confirmation from the user.
			for (String name : freqTable.keySet())
			{
				if (freqTable.get(name).intValue() > 1) localNames.add(name);
			}
		}
		
		return localNames;
	}
	
	/**
	 * Scans conversations and creates a list of all
	 * accounts that must be created, along with the
	 * aliases under which they may appear.
	 */
	protected void _gatherAccountInfo()
	{
		NameIndex<Set<String>> aliasesIndex = new NameIndex<Set<String>>();
		
		// Compute aliases table
		for (GaimConversationInfo conv : this._conversations)
		{			
			if (conv.isConference) continue;
			
			for (String name : conv.speakers.keySet())
			{
				// Trivial aliases are omitted
				if (name.equals(conv.localAccountName) || name.equals(conv.remoteAccountName)) continue;
				
				String accName = this._localNames.contains(name) ? conv.localAccountName : conv.remoteAccountName;
				
				if (!aliasesIndex.itemExists(conv.service, accName))
					aliasesIndex.addItem(conv.service, accName, new TreeSet<String>());
				aliasesIndex.getItem(conv.service, accName).add(name);
			}
		}
		
		// Assemble account info
		this._accounts = new NameIndex<ImportedAccountInfo>();
		for (int i=0; i<2; i++)
		{
			// Note: do not refactor this loop. There's a very specific
			// reason why all local accounts must be added first (a local
			// account may also appear as a remote account)
			for (GaimConversationInfo conv : this._conversations)
			{
				String accName = (i==0) ? conv.localAccountName : conv.remoteAccountName;
				
				if (!this._accounts.itemExists(conv.service, accName))
				{
					Set<String> aliases = aliasesIndex.getItem(conv.service, accName);
					if (aliases == null) aliases = new TreeSet<String>();
					
					this._accounts.addItem(conv.service, accName, new ImportedAccountInfo(
							new FreeAccount(conv.service, accName), aliases, (i==0))
							);
				}
			}
		}
	}
	
	/**
	 * Scans all conversations and attempts to identify
	 * the speakers.
	 * 
	 * @return A list of aliases that need to be resolved
	 *         manually by the user before all speakers can
	 *         be identified
	 */
	protected List<Alias> _identifySpeakers()
	{
		NameIndex<FreeAccount> aliases = new NameIndex<FreeAccount>();
		NameIndex<Alias> unresolved = new NameIndex<Alias>();
		
		// Gather aliases by service (useful for conferences)
		for (ImportedAccountInfo accInfo : this._accounts.getAllItems())
		{
			for (String name : accInfo.aliases)
				aliases.addItem(accInfo.account.service, name, accInfo.account);
			
			// Also add trivial aliases
			aliases.addItem(accInfo.account.service, accInfo.account.name, accInfo.account);
		}

		// Scan conversations
		for (GaimConversationInfo conv : this._conversations)
		{
			FreeAccount localAccount = this._accounts.getItem(conv.service, conv.localAccountName).account;
			FreeAccount remoteAccount = this._accounts.getItem(conv.service, conv.remoteAccountName).account;
			
			for (Map.Entry<String, FreeAccount> speaker : conv.speakers.entrySet())
			{
				speaker.setValue(null); // clear identification
				
				String name = speaker.getKey();
				
				// Identify local account
				if (name.equals(conv.localAccountName) || this._localNames.contains(name))
				{
					speaker.setValue(localAccount);
					continue;
				}
				
				// In a non-conference conversation, there are only two
				// speakers, so if it's not the local account, it has to
				// be the remote account speaking
				if (!conv.isConference)
				{
					speaker.setValue(remoteAccount);
					continue;
				}
				
				// Otherwise, check if this is an alias for any account
				// for this service
				speaker.setValue(aliases.getItem(conv.service, name));
				
				// The speaker is still unidentified, make note of this
				if (speaker.getValue() == null) unresolved.addItem(conv.service, name, new Alias(conv.service, name, null));
			}
		}
		
		return unresolved.getAllItems();
	}
	
	/**
	 * Finalizes speaker identification for all conversation using
	 * a list of user-supplied aliases. Following this procedure,
	 * all speakers should be identified.
	 * 
	 * @param resolvedAliases A list of resolved aliases
	 */
	protected void _finalizeSpeakerIdentification(List<Alias> resolvedAliases)
	{
		NameIndex<FreeAccount> resolved = new NameIndex<FreeAccount>();
		for (Alias alias : resolvedAliases)
			resolved.addItem(alias.service, alias.name, alias.resolution);
		
		// Scan conversations
		for (GaimConversationInfo conv : this._conversations)
			for (Map.Entry<String, FreeAccount> speaker : conv.speakers.entrySet())
				if (speaker.getValue() == null)
				{
					String name = speaker.getKey();
					FreeAccount account = resolved.getItem(conv.service, name);
					
					if (account == null)
						throw new RuntimeException("Speaker '"+name+"' is still unidentified");
					
					speaker.setValue(account);
				}
	}
	
	/**
	 * Given a list of user-supplied resolved aliases, adds any accounts
	 * created for the purpose of resolving an alias to the main database.
	 * 
	 * @param resolvedAliases  A list of resolved aliases
	 */
	protected void _mergeResolvedAliasAccounts(List<Alias> resolvedAliases)
	{
		for (Alias alias : resolvedAliases)
		{
			FreeAccount account = alias.resolution;
			
			if (!this._accounts.itemExists(account.service, account.name))
			{
				List<String> names = new ArrayList<String>();
				names.add(alias.name);
				
				this._accounts.addItem(account.service, account.name, new ImportedAccountInfo(account, names, false));
			}
		}
	}
	
	/**
	 * Executes the final archive conversion operation.
	 * All speakers must be identified by this point.
	 * 
	 * @return The GAIM archive in internal format.
	 */
	protected IMArchive _convertArchive() throws Exception
	{
		// Create archive
		IMArchive archive = new IMArchive();
		Group defaultGroup = archive.createGroup("Default");
		
		// Add identities and identity contacts
		for (ImportedAccountInfo accInfo : this._accounts.getAllItems())
			if (accInfo.isLocal)
			{
				// Determine an identity name under which to file this
				// account, using the first alias if available
				String identName = (accInfo.aliases.length > 0) ? accInfo.aliases[0] : accInfo.account.name;
				
				archive.createIdentity(identName).createAccount(accInfo.account.service, accInfo.account.name);
			}
		
		// Add contacts and regular accounts
		for (ImportedAccountInfo accInfo : this._accounts.getAllItems())
			if (!accInfo.isLocal)
			{
				// Determine a contact name under which to file this
				// account, using the first alias if available
				String contactName = (accInfo.aliases.length > 0) ? accInfo.aliases[0] : accInfo.account.name;
				
				// If this name is already taken by a similarly-named identity,
				// add an asterisk
				Contact contact;
				while (((contact = archive.getContactByName(contactName)) != null) && contact.isIdentity())
					contactName += "*";
				
				defaultGroup.createContact(contactName).createAccount(accInfo.account.service, accInfo.account.name);
			}		
		
		// Convert and add conversations
		int processed = 0;
		
		_notifyProgress("Converting conversations...", 0, this._conversations.size());
		
		for (GaimConversationInfo conv : this._conversations)
		{
			this._convertConversation(archive, conv);
			
			processed++;
			_notifyProgress("Converting conversations...", processed, this._conversations.size());
		}
		
		return archive;
	}
	
	/**
	 * Converts a single GAIM conversation to internal
	 * format.
	 * 
	 * @param archive The archive containing all the accounts
	 * @param gaimConv A GAIM conversation info object
	 * @return A conversation object in internal format, replies included
	 */
	protected Conversation _convertConversation(IMArchive archive, GaimConversationInfo gaimConv) throws Exception
	{
		// Create conversation
		Conversation conv = archive.createConversation(gaimConv.dateStarted,
			archive.getAccountByName(gaimConv.service, gaimConv.localAccountName),
			archive.getAccountByName(gaimConv.service, gaimConv.remoteAccountName),
			gaimConv.isConference);
		
		// Convert speakers
		for (String name : gaimConv.speakers.keySet())
		{
			FreeAccount freeAcc = gaimConv.speakers.get(name);
			conv.addSpeaker(name, archive.getAccountByName(freeAcc.service, freeAcc.name));
		}
				
		// Convert replies
		for (RawReply rawReply : gaimConv)
			conv.addReply(rawReply.date, (rawReply.sender != null) ? conv.getSpeakerByName(rawReply.sender) : null, rawReply.text);
		
		return conv;
	}
}
