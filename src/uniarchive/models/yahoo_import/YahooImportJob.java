/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.yahoo_import;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMService;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for importing a Yahoo! Messenger archive.
 */
public class YahooImportJob
{
	protected File _archivePath;
	protected ProgressListener _progressListener;
	
	/**
	 * Constructor for a Yahoo import job.
	 * 
	 * @param archivePath The path to the folder containing the Yahoo archive
	 * @param progressListener An object that will be notified of any progress in the
	 *                         import (or NULL if this is not needed)
	 */
	public YahooImportJob(File archivePath, ProgressListener progressListener)
	{
		this._archivePath = archivePath;
		this._progressListener = progressListener;
		
		this._notifyProgress("Awaiting start command", 0, 0);
	}
	
	/**
	 * Runs the import.
	 * 
	 * @return The converted archive
	 * @throws Exception
	 */
	public IMArchive run() throws Exception
	{
		return this._convertArchive(this._loadConversations(this._scanForConversations()));
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
			else if (file.isFile() && file.getName().toLowerCase().endsWith(".dat"))
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
	protected List<YahooConversationInfo> _loadConversations(List<File> convFiles)
	{
		int processed = 0;
		
		List<YahooConversationInfo> conversations = new ArrayList<YahooConversationInfo>();
		_notifyProgress("Analyzing conversation files...", 0, convFiles.size());
		
		for (File convFile : convFiles)
		{
			conversations.addAll(YahooConversationInfo.loadFromFile(convFile));
			
			processed++;
			_notifyProgress("Analyzing conversation files...", processed, convFiles.size());
		}
		
		return conversations;
	}
	
	/**
	 * Executes the final archive conversion operation.
	 * 
	 * @param conversations A list of conversations in raw format
	 * @return The Yahoo archive in internal format.
	 */
	protected IMArchive _convertArchive(List<YahooConversationInfo> conversations) throws Exception
	{
		// Create archive
		IMArchive archive = new IMArchive();
		Group defaultGroup = archive.createGroup("Default");
		
		// Create identities and identity accounts
		for (YahooConversationInfo conv : conversations)
			if (archive.getContactByName(conv.localAccountName) == null)
				archive.createIdentity(conv.localAccountName)
					.createAccount(IMService.YAHOO, conv.localAccountName);
		
		// Add regular contacts and accounts
		for (YahooConversationInfo conv : conversations)
		{
			if (archive.getContactByName(conv.remoteAccountName) == null)
				defaultGroup.createContact(conv.remoteAccountName)
					.createAccount(IMService.YAHOO, conv.remoteAccountName);
			
			for (String speaker : conv.speakerAccounts)
				if (!speaker.equals(conv.localAccountName) && !speaker.equals(conv.remoteAccountName))
					if (archive.getContactByName(speaker) == null)
						defaultGroup.createContact(speaker)
							.createAccount(IMService.YAHOO, speaker);
		}
		
		// Convert and add conversations
		int processed = 0;
		
		_notifyProgress("Converting conversations...", 0, conversations.size());
		
		for (YahooConversationInfo conv : conversations)
		{
			this._convertConversation(archive, conv);
			
			processed++;
			_notifyProgress("Converting conversations...", processed, conversations.size());
		}
		
		return archive;
	}
	
	/**
	 * Converts a single Yahoo conversation to internal format.
	 * 
	 * @param archive The archive, with all accounts loaded
	 * @param yahooConv A Yahoo conversation info object
	 * @return A conversation object in internal format, replies
	 *         included
	 */
	protected Conversation _convertConversation(IMArchive archive, YahooConversationInfo yahooConv) throws Exception
	{
		// Create conversation
		Conversation conv = archive.createConversation(yahooConv.dateStarted,
			archive.getAccountByName(IMService.YAHOO, yahooConv.localAccountName),
			archive.getAccountByName(IMService.YAHOO, yahooConv.remoteAccountName),
			yahooConv.isConference);
		
		// Convert speakers
		for (String name : yahooConv.speakerAccounts)
			conv.addSpeaker(name, archive.getAccountByName(IMService.YAHOO, name));
		
		// Convert replies
		for (RawReply rawReply : yahooConv)
		{
			String text;
			switch (rawReply.type)
			{
			case CONFERENCE_JOIN:
				text = ((String)rawReply.extra)+" has joined the conference";
				break;
			case CONFERENCE_LEAVE:
				text = ((String)rawReply.extra)+" has left the conference";
				break;
			case CONFERENCE_DECLINE:
				text = "Declined conference with message: "+rawReply.text;
				break;
			default:
				text = this._filterReplyText(rawReply.text);
				break;
			}

			conv.addReply(rawReply.date, (rawReply.sender != null) ? conv.getSpeakerByName(rawReply.sender) : null, text);
		}
	
		// Convert conversation
		return conv;
	}
	
	/**
	 * Filters a Yahoo reply text, eliminating font and color
	 * changes.
	 * 
	 * @param text The original reply text
	 * @return The processed reply
	 */
	protected String _filterReplyText(String text)
	{
		final String PAT_ANSI_LIKE = "\\x1B\\x5B[^m]*m";
		final String PAT_HTML_LIKE = "(?i)</?(font|fade)[^>]*>";
		final String PAT_FORMATTING = "("+PAT_ANSI_LIKE+"|"+PAT_HTML_LIKE+")";
		
		StringBuilder result = new StringBuilder();
		for (String part : text.split(PAT_FORMATTING)) result.append(part);
		
		return result.toString();
	}
}
