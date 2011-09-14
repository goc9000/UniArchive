/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.yahoo_import;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for storing information regarding a Yahoo conversation.
 * 
 * Note that replies are not stored, in order to save memory.
 * Instead, an iterator interface is offered, allowing replies
 * to be read when they are needed from the underlying file. 
 */
public class YahooConversationInfo implements Iterable<RawReply>
{
	public final File file;
	public final int offsetInFile;
	public final int sizeInFile;
	public final Date dateStarted;
	public final String localAccountName;
	public final String remoteAccountName;
	public final boolean isConference;
	public final Set<String> speakerAccounts;
	
	/**
	 * Constructor.
	 * 
	 * @param dateStarted The conversation start date
	 * @param localAccountName The local account name
	 * @param remoteAccountName The remote account name
	 * @param isConference Whether or not the conversation is a conference
	 * @param speakers A set of strings identifying speakers in the conversation
	 *                 (in Yahoo archives, a speaker's name is identical to its
	 *                 account, hence no need for account mapping)
	 * @param file The underlying file for the conversation
	 * @param offsetInFile The position at which replies for this conversation
	 *                     begin in the underlying file
	 * @param sizeInFile The span of this conversation in the underlying file
	 */
	private YahooConversationInfo(Date dateStarted, String localAccountName, String remoteAccountName,
			boolean isConference, Set<String> speakers, File file, int offsetInFile, int sizeInFile)
	{
		this.dateStarted = dateStarted;
		this.localAccountName = localAccountName;
		this.remoteAccountName = remoteAccountName;
		this.isConference = isConference;
		this.speakerAccounts = new TreeSet<String>(speakers);
		this.file = file;
		this.offsetInFile = offsetInFile;
		this.sizeInFile = sizeInFile;
	}
	
	/**
	 * Gets an iterator for this conversation's replies
	 * as they are stored on disk.
	 * 
	 * @return A RawReply iterator
	 */
	public Iterator<RawReply> iterator()
	{
		return new YahooConversationReader(this.file, this.localAccountName, this.remoteAccountName,
				this.offsetInFile, this.sizeInFile);
	}	
	
	/**
	 * Reads all the Yahoo conversations in a Yahoo archive file.
	 * 
	 * @param conversationFile The file containing the conversations. Note that
	 *                         the file needs to have its original name and place
	 *                         in the Yahoo! archive folder for the function to be
	 *                         able to read the local and remote account names.
	 * @return A list of YahooConversationInfo objects
	 */
	public static List<YahooConversationInfo> loadFromFile(File conversationFile)
	{
		final String PAT_SEP = Pattern.quote(File.separator);
		final String PAT_YAHOO_ID = "[a-z0-9_.-]+";
		final Pattern PAT_CONV_FILENAME = Pattern.compile(
				PAT_SEP+"(conferences|messages)"+
				PAT_SEP+"("+PAT_YAHOO_ID+")"+
				PAT_SEP+"[0-9]{8}-("+PAT_YAHOO_ID+")[.]dat$",
				Pattern.CASE_INSENSITIVE);
		
		List<YahooConversationInfo> conversations = new ArrayList<YahooConversationInfo>();
		
		try
		{
			// Analyze filename so as to get the local and remote account
			// names, as well as the conference indicator
			Matcher match = PAT_CONV_FILENAME.matcher(conversationFile.getPath());
			if (!match.find()) throw new RuntimeException("Filename does not match the pattern of a "+
					"conversation file situated in a Yahoo archive:\n"+
					".../{Conferences|Messages}/{remote-id}/{date}-{local-id}.dat");
			boolean isConference = match.group(1).toLowerCase().equals("conferences");
			String remoteAccountName = match.group(2);
			String localAccountName = match.group(3);
			
			// Now start reading records and create conversations as we go
			YahooConversationReader reader = new YahooConversationReader(conversationFile, localAccountName,
					remoteAccountName, 0, -1);
			
			boolean haveConv = false;
			Set<String> speakers = new TreeSet<String>();
			Date dateStarted = null;
			int startPos = 0, lastPos = 0;
			
			while (reader.hasNext())
			{
				RawReply reply = reader.next();
				if (reply.type == RawReply.Type.START_CONV)
				{
					if (haveConv) conversations.add(new YahooConversationInfo(dateStarted, localAccountName,
							remoteAccountName, isConference, speakers, conversationFile, startPos, lastPos-startPos));
					
					dateStarted = reply.date;
					speakers.clear();
					startPos = reader.getBufferPosition();
				}
				
				if (dateStarted == null) dateStarted = reply.date;
				haveConv = true;
				
				if (reply.sender != null) speakers.add(reply.sender);
				if (reply.type == RawReply.Type.CONFERENCE_JOIN) speakers.add((String)reply.extra);
				if (reply.type == RawReply.Type.CONFERENCE_LEAVE) speakers.add((String)reply.extra);
				
				lastPos = reader.getBufferPosition();
			}
			if (haveConv) conversations.add(new YahooConversationInfo(dateStarted, localAccountName,
					remoteAccountName, isConference, speakers, conversationFile, startPos, lastPos-startPos));
			
			return conversations;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot read conversation #"+(conversations.size()+1)+
					" in file "+conversationFile+":\n"+e.getMessage());
		}
	}
}
