/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.digsby_import;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uniarchive.models.archive.IMService;

/**
 * Class/structure for storing information regarding a Digsby
 * conversation.
 * 
 * Note that replies are not stored, in order to save memory.
 * Instead, an iterator interface is offered, allowing replies
 * to be read when they are needed from the underlying file. 
 */
public class DigsbyConversationInfo implements Iterable<RawReply>
{
	protected final static long MAX_TIME_BETWEEN_REPLIES_MS = 20*60*1000; // 20 minutes
	
	public final File file;
	public final int replyOffset;
	public final int replyCount;
	public final Date dateStarted;
	public final IMService localService;
	public final String localAccountName;
	public final IMService remoteService;
	public final String remoteAccountName;
	public final boolean isConference;
	public final Set<String> speakers;
	
	/**
	 * Constructor.
	 * 
	 * @param dateStarted The conversation start date
	 * @param localAccountName The local account name
	 * @param remoteAccountName The remote account name
	 * @param localService The conversation service for the local account
	 * @param remoteService The conversation service for the remote account
	 * @param isConference Whether or not the conversation is a conference
	 * @param speakers A set of strings identifying speakers in the conversation.
	 *                 In Digsby conversations, these are identical to the
	 *                 corresponding account names.
	 * @param file The underlying file for the conversation
	 * @param replyOffset The reply index at which replies for this conversation
	 *                    begin in the underlying file
	 * @param replyCount The span of this conversation in the underlying file,
	 *                   in terms of replies
	 */
	private DigsbyConversationInfo(Date dateStarted, IMService localService, String localAccountName, IMService remoteService,
			String remoteAccountName, boolean isConference, Set<String> speakers, File file, int replyOffset, int replyCount)
	{
		this.dateStarted = dateStarted;
		this.localService = localService;
		this.localAccountName = localAccountName;
		this.remoteService = remoteService;
		this.remoteAccountName = remoteAccountName;
		this.isConference = isConference;
		this.speakers = new TreeSet<String>(speakers);
		this.file = file;
		this.replyOffset = replyOffset;
		this.replyCount = replyCount;
	}
	
	/**
	 * Gets an iterator for this conversation's replies
	 * as they are stored on disk.
	 * 
	 * @return A RawReply iterator
	 */
	public Iterator<RawReply> iterator()
	{
		return new DigsbyConversationReader(this.file, this.replyOffset, this.replyCount);
	}	
	
	/**
	 * Reads all the Digsby conversations in a Digsby archive file.
	 * 
	 * @param conversationFile The file containing the conversations. Note that
	 *                         the file needs to have its original name and place
	 *                         in the Digsby archive folder for the function to be
	 *                         able to read the local and remote account names.
	 * @return A list of DigsbyConversationInfo objects
	 */
	public static List<DigsbyConversationInfo> loadFromFile(File conversationFile)
	{
		final String PAT_SEP = Pattern.quote(File.separator);
		final String PAT_ACCOUNT_NAME = "[a-z0-9_.-@]+";
		final String PAT_SERVICE_NAME = "[a-z-]+";
		final Pattern PAT_CONV_FILENAME = Pattern.compile(
				PAT_SEP+"("+PAT_SERVICE_NAME+")"+
				PAT_SEP+"("+PAT_ACCOUNT_NAME+")"+
				PAT_SEP+"("+PAT_ACCOUNT_NAME+")_("+PAT_SERVICE_NAME+")"+
				PAT_SEP+"[0-9]{4}-[0-9]{2}-[0-9]{2}[.]html$",
				Pattern.CASE_INSENSITIVE);
		
		List<DigsbyConversationInfo> conversations = new ArrayList<DigsbyConversationInfo>();
		
		try
		{
			// Analyze filename so as to get the local and remote account
			// names, as well as the local and remote services
			Matcher match = PAT_CONV_FILENAME.matcher(conversationFile.getPath());
			if (!match.find()) throw new RuntimeException("Filename does not match the pattern of a "+
					"conversation file situated in a Digsby archive:\n"+
					".../{service}/{local-account}/{remote-account}_{remote-service}/{date}.html");
			IMService localService = _interpretService(match.group(1));
			String localAccountName = match.group(2);
			String remoteAccountName = match.group(3);
			IMService remoteService = _interpretService(match.group(4));
						
			// Now start reading replies and create conversations as we go
			Set<String> speakers = new TreeSet<String>();
			Date dateStarted = null;
			Date lastReplyDate = null;
			int replyIndex = 0, startIndex = 0;
			
			DigsbyConversationReader reader = new DigsbyConversationReader(conversationFile, 0, Integer.MAX_VALUE);
			while (reader.hasNext())
			{
				RawReply reply = reader.next();
				
				// Note: conversations occuring on the same day are merged by
				// Digsby in the archive, so we're using a "time between replies"
				// criterion to separate messages
				if ((lastReplyDate != null) && (reply.date.getTime() > lastReplyDate.getTime() + MAX_TIME_BETWEEN_REPLIES_MS))
				{
					// Note: Digsby doesn't have any conference indicators, so we'll just assume that
					// a conference is any conversation with more than two speakers
					
					conversations.add(new DigsbyConversationInfo(
							dateStarted,
							localService,
							localAccountName,
							remoteService,
							remoteAccountName,
							speakers.size() > 2,
							speakers,
							conversationFile,
							startIndex,
							replyIndex-startIndex));
					
					dateStarted = null;
					startIndex = replyIndex;
					speakers.clear();
				}
				
				if (dateStarted == null) dateStarted = reply.date;
				speakers.add(reply.sender);
				
				lastReplyDate = reply.date;
				replyIndex++;
			}
			
			if (dateStarted != null)
				conversations.add(new DigsbyConversationInfo(
						dateStarted,
						localService,
						localAccountName,
						remoteService,
						remoteAccountName,
						speakers.size() > 2,
						speakers,
						conversationFile,
						startIndex,
						replyIndex-startIndex));
			
			return conversations;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot read conversation #"+(conversations.size()+1)+
					" in file "+conversationFile+":\n"+e.getMessage());
		}
	}
	
	/**
	 * Interprets a Digsby service string.
	 * 
	 * @param serviceName The service name
	 * @return The corresponding IMService constant
	 */
	protected static IMService _interpretService(String serviceName)
	{
		if (serviceName.equals("digsby")) return IMService.DIGSBY;
		if (serviceName.equals("yahoo")) return IMService.YAHOO;
		if (serviceName.equals("msn")) return IMService.MSN;
		if (serviceName.equals("gtalk")) return IMService.GTALK;
		
		throw new RuntimeException("Unrecognized service: '"+serviceName+"'");	
	}
}
