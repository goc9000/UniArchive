/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.msn_import;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class/structure for storing information regarding a MSN
 * conversation.
 * 
 * Note that replies are not stored, in order to save memory.
 * Instead, an iterator interface is offered, allowing replies
 * to be read when they are needed from the underlying file. 
 */
public class MsnConversationInfo implements Iterable<RawReply>
{
	public final File file;
	public final int sessionId;
	public final Date dateStarted;
	public final String accountNameGuess;
	public final boolean isConference;
	public final Set<String> speakers;
	public final Map<String, Set<String>> interactions;
	
	/**
	 * Constructor.
	 * 
	 * @param dateStarted The conversation start date
	 * @param accountNameGuess A guess at the remote account name,
	 *                         extracted from the conversation filename
	 * @param isConference Whether or not the conversation is a conference
	 * @param speakers A set of strings identifying speakers in the conversation
	 * @param interactions A map linking every speaker to a set of speakers he
	 *                     sends replies to during this conversation
	 * @param file The underlying file for the conversation
	 * @param sessionId The session ID for this conversation's replies in the file
	 */
	private MsnConversationInfo(Date dateStarted, String accountNameGuess, boolean isConference,
		Set<String> speakers, Map<String, Set<String>> interactions, File file, int sessionId)
	{
		if (speakers.size() < 2)
			throw new RuntimeException("Cannot import conversation with only one speaker");
		
		this.dateStarted = dateStarted;
		this.accountNameGuess = accountNameGuess;
		this.isConference = isConference;
		this.speakers = new TreeSet<String>(speakers);
		this.interactions = new TreeMap<String, Set<String>>();
		for (String sender : interactions.keySet())
			this.interactions.put(sender, new TreeSet<String>(interactions.get(sender)));
		this.file = file;
		this.sessionId = sessionId;
	}
	
	/**
	 * Gets an iterator for this conversation's replies
	 * as they are stored on disk.
	 * 
	 * @return A RawReply iterator
	 */
	public Iterator<RawReply> iterator()
	{
		return new MsnConversationReader(this.file, this.sessionId);
	}
	
	/**
	 * Reads all the MSN conversations in a MSN archive file.
	 * 
	 * @param conversationFile The file containing the conversations.
	 * @return A list of MsnConversationInfo objects
	 */
	public static List<MsnConversationInfo> loadFromFile(File conversationFile)
	{
		final Pattern PAT_ACCOUNT_GUESS = Pattern.compile("^([a-z_.-]+).*\\.xml$", Pattern.CASE_INSENSITIVE);
		
		List<MsnConversationInfo> conversations = new ArrayList<MsnConversationInfo>();
		
		try
		{
			// Analyze filename so as to try to guess the account name
			Matcher match = PAT_ACCOUNT_GUESS.matcher(conversationFile.getName());
			String accountGuess = match.find() ? match.group(1) : null;
			
			// Now start reading replies and gather information
			// about existing conversations
			Map<Integer,Set<String>> speakers = new TreeMap<Integer,Set<String>>();
			Map<Integer,Map<String,Set<String>>> interactions = new TreeMap<Integer,Map<String,Set<String>>>();
			Map<Integer,Date> startDates = new TreeMap<Integer,Date>();
			Map<Integer,Boolean> isConference = new TreeMap<Integer,Boolean>();
			
			MsnConversationReader reader = new MsnConversationReader(conversationFile, -1);
			while (reader.hasNext())
			{
				RawReply reply = reader.next();
				if (!startDates.containsKey(reply.sessionId))
				{
					startDates.put(reply.sessionId, reply.date);
					isConference.put(reply.sessionId, Boolean.FALSE);
					speakers.put(reply.sessionId, new TreeSet<String>());
					interactions.put(reply.sessionId, new TreeMap<String,Set<String>>());
				}
				speakers.get(reply.sessionId).add(reply.sender);
				if (reply.receiver != null)
				{
					speakers.get(reply.sessionId).add(reply.receiver);
					
					Map<String,Set<String>> interMap = interactions.get(reply.sessionId);
					if (!interMap.containsKey(reply.sender))
						interMap.put(reply.sender, new TreeSet<String>());
					interMap.get(reply.sender).add(reply.receiver);
				}
				
				switch (reply.type)
				{
				case CONFERENCE_JOIN:
				case CONFERENCE_LEAVE:
					isConference.put(reply.sessionId, Boolean.TRUE);
				}
			}
			
			// Now create said conversations
			for (Integer sessId : startDates.keySet())
			{
				conversations.add(new MsnConversationInfo(
						startDates.get(sessId),
						accountGuess,
						isConference.get(sessId).booleanValue(),
						speakers.get(sessId),
						interactions.get(sessId),
						conversationFile,
						sessId.intValue()));
			}

			return conversations;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot read conversation #"+(conversations.size()+1)+
					" in file "+conversationFile+":\n"+e.getMessage());
		}
	}
}
