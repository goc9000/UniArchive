/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.digsby_import;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for importing a Digsby archive.
 */
public class DigsbyImportJob
{
	protected File _archivePath;
	protected ProgressListener _progressListener;
	
	/**
	 * Constructor for a Digsby import job.
	 * 
	 * @param archivePath The path to the folder containing the Digsby archive
	 * @param progressListener An object that will be notified of any progress in the
	 *                         import (or NULL if this is not needed)
	 */
	public DigsbyImportJob(File archivePath, ProgressListener progressListener)
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
			else if (file.isFile() && file.getName().toLowerCase().endsWith(".html"))
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
	protected List<DigsbyConversationInfo> _loadConversations(List<File> convFiles)
	{
		int processed = 0;
		
		List<DigsbyConversationInfo> conversations = new ArrayList<DigsbyConversationInfo>();
		_notifyProgress("Analyzing conversation files...", 0, convFiles.size());
		
		for (File convFile : convFiles)
		{
			conversations.addAll(DigsbyConversationInfo.loadFromFile(convFile));
			
			processed++;
			_notifyProgress("Analyzing conversation files...", processed, convFiles.size());
		}
		
		return conversations;
	}
	
	/**
	 * Executes the final archive conversion operation.
	 * 
	 * @param conversations A list of conversations in raw format
	 * @return The Digsby archive in internal format.
	 */
	protected IMArchive _convertArchive(List<DigsbyConversationInfo> conversations) throws Exception
	{
		// Create archive
		IMArchive archive = new IMArchive();
		Group defaultGroup = archive.createGroup("Default");
		
		// Create identities and identity accounts
		for (DigsbyConversationInfo conv : conversations)
			if (archive.getContactByName(conv.localAccountName) == null)
				archive.createIdentity(conv.localAccountName)
					.createAccount(conv.localService, conv.localAccountName);
		
		// Add contacts and accounts
		for (DigsbyConversationInfo conv : conversations)
		{
			if (archive.getContactByName(conv.remoteAccountName) == null)
				defaultGroup.createContact(conv.remoteAccountName)
					.createAccount(conv.remoteService, conv.remoteAccountName);
			
			for (String speaker : conv.speakers)
				if (!speaker.equals(conv.localAccountName) && !speaker.equals(conv.remoteAccountName))
					if (archive.getContactByName(speaker) == null)
						defaultGroup.createContact(speaker)
							.createAccount(conv.remoteService, speaker);
		}
		
		// Convert and add conversations
		int processed = 0;
		
		_notifyProgress("Converting conversations...", 0, conversations.size());
		
		for (DigsbyConversationInfo conv : conversations)
		{
			this._convertConversation(archive, conv);
			
			processed++;
			_notifyProgress("Converting conversations...", processed, conversations.size());
		}
		
		return archive;
	}
	
	/**
	 * Converts a single Digsby conversation to internal format.
	 * 
	 * @param archive The archive, with all accounts loaded
	 * @param digsbyConv A Yahoo conversation info object
	 * @return A conversation object in internal format, replies included
	 */
	protected Conversation _convertConversation(IMArchive archive, DigsbyConversationInfo digsbyConv) throws Exception
	{
		// Create conversation
		Conversation conv = archive.createConversation(digsbyConv.dateStarted,
			archive.getAccountByName(digsbyConv.localService, digsbyConv.localAccountName),
			archive.getAccountByName(digsbyConv.remoteService, digsbyConv.remoteAccountName),
			digsbyConv.isConference);
		
		// Convert speakers
		for (String name : digsbyConv.speakers)
			conv.addSpeaker(name, archive.getContactByName(name).getAccounts().get(0));
		
		// Convert replies
		for (RawReply rawReply : digsbyConv)
			conv.addReply(rawReply.date, conv.getSpeakerByName(rawReply.sender), rawReply.text);
		
		// Convert conversation
		return conv;
	}
}
