/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uniarchive.models.archive.FreeAccount;
import uniarchive.models.archive.IMService;

/**
 * Class/structure for storing information regarding a GAIM
 * conversation.
 * 
 * Note that replies are not stored, in order to save memory.
 * Instead, an iterator interface is offered, allowing replies
 * to be read when they are needed from the underlying file. 
 */
public class GaimConversationInfo implements Iterable<RawReply>
{
	public File file;
	public Date dateStarted;
	public IMService service;
	public String localAccountName;
	public String remoteAccountName;
	public boolean isConference;
	public Map<String, FreeAccount> speakers;
	
	/**
	 * Constructor.
	 * 
	 * @param dateStarted The conversation start date
	 * @param service The conversation service
	 * @param localAccountName The local account name
	 * @param remoteAccountName The remote account name
	 * @param isConference Whether or not the conversation is a conference
	 * @param speakers A map of strings, representing speakers in this
	 *                 conversation, to free accounts (since speaker
	 *                 names are not globally unique in GAIM, the speaker->account
	 *                 resolution is done individually per conversation)
	 * @param file The underlying file for the conversation
	 */
	private GaimConversationInfo(Date dateStarted, IMService service, String localAccountName, String remoteAccountName,
			boolean isConference, Map<String, FreeAccount> speakers, File file)
	{
		this.dateStarted = dateStarted;
		this.service = service;
		this.localAccountName = localAccountName;
		this.remoteAccountName = remoteAccountName;
		this.isConference = isConference;
		this.file = file;
		this.speakers = new TreeMap<String, FreeAccount>();
		for (String speaker : speakers.keySet())
			this.speakers.put(speaker, speakers.get(speaker));
	}
	
	/**
	 * Gets an iterator for this conversation's replies
	 * as they are stored on disk.
	 * 
	 * @return A RawReply iterator
	 */
	public Iterator<RawReply> iterator()
	{
		return new GaimConversationReader(this.file, this.dateStarted);
	}
	
	/**
	 * Groups conversations by the service involved.
	 * 
	 * @param conversations A list of conversations that requiring grouping
	 * @return A map indexing lists of conversations by the service used.
	 */
	public static Map<IMService,List<GaimConversationInfo>> groupByService(List<GaimConversationInfo> conversations)
	{
		Map<IMService,List<GaimConversationInfo>> grouping = new TreeMap<IMService,List<GaimConversationInfo>>();
		
		for (GaimConversationInfo conv : conversations)
		{
			if (!grouping.containsKey(conv.service))
				grouping.put(conv.service, new ArrayList<GaimConversationInfo>());
			
			grouping.get(conv.service).add(conv);
		}
		
		return grouping;
	}
	
	/**
	 * Groups conversations by the remote account involved.
	 * 
	 * @param conversations A list of conversations that requiring grouping
	 * @return A map indexing lists of conversations by the other account name.
	 */
	public static Map<String,List<GaimConversationInfo>> groupByRemoteAccount(List<GaimConversationInfo> conversations)
	{
		Map<String,List<GaimConversationInfo>> grouping = new TreeMap<String,List<GaimConversationInfo>>();
		
		for (GaimConversationInfo conv : conversations)
		{
			if (!grouping.containsKey(conv.remoteAccountName))
				grouping.put(conv.remoteAccountName, new ArrayList<GaimConversationInfo>());
			
			grouping.get(conv.remoteAccountName).add(conv);
		}
		
		return grouping;
	}
	
	/**
	 * Reads all the GAIM conversations in a GAIM archive file.
	 * This returns either one or zero conversations (i.e. for an
	 * empty conversation file, which can occur with GAIM)
	 * 
	 * @param conversationFile The file containing the conversations.
	 * @return A list of GaimConversationInfo objects
	 */
	public static List<GaimConversationInfo> loadFromFile(File conversationFile)
	{
		final Pattern PAT_CONV_HEADER = Pattern.compile("^Conversation with ([\\w.@-]+)/? at .* on ([\\w.@-]+)/? \\(([\\w]+)\\)$");
		
		List<GaimConversationInfo> conversations = new ArrayList<GaimConversationInfo>();
		
		try
		{
			// Extract the conversation date from the filename.
			// Note that the conversation date also appears in the header,
			// but the format varies widely according to the user's local
			// settings. The filename is a much more portable clue.
			Date dateStarted = _interpretFilenameDate(conversationFile);
			
			// Extract and analyze conversation header
			GaimFileReader reader = GaimFileReader.forFile(conversationFile);
			String convHeader = reader.readLine();
			if (convHeader == null) return conversations;
			Matcher matcher = PAT_CONV_HEADER.matcher(convHeader);
			if (!matcher.find()) throw new RuntimeException("Invalid conversation header:\n"+convHeader);
			
			String remoteAccountName = matcher.group(1);
			String localAccountName = matcher.group(2);
			IMService service = _interpretService(matcher.group(3));
			
			// Check whether this is a conference
			boolean isConference = false;
			if (conversationFile.getParentFile().getName().endsWith(".chat"))
			{
				isConference = true;
				remoteAccountName = remoteAccountName.substring(0, remoteAccountName.lastIndexOf("-"));
			}
			
			// Now analyze replies and determine speakers
			Map<String, FreeAccount> speakers = new TreeMap<String, FreeAccount>();
			GaimConversationReader replyReader = new GaimConversationReader(conversationFile, dateStarted);
			while (replyReader.hasNext())
			{
				RawReply reply = replyReader.next();
			
				if (reply.type == RawReply.Type.CONFERENCE_JOIN)
				{
					speakers.put(reply.conferenceJoinData.name, null);
					isConference = true;	
				}
				if (reply.sender == null) continue;
				
				speakers.put(reply.sender, null);
			}
			
			// Now create the conversation
			conversations.add(new GaimConversationInfo(dateStarted, service, localAccountName, remoteAccountName,
					isConference, speakers, conversationFile));

			return conversations;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error reading conversations in file "+conversationFile+":\n"+e.getMessage());
		}
	}
	
	/**
	 * Reads and interprets the conversation date encoded in
	 * a conversation filename. If the filename does
	 * not represent a date, an exception is thrown.
	 * 
	 * @param conversationFile The conversation file
	 * @return The conversation date
	 */
	protected static Date _interpretFilenameDate(File conversationFile)
	{
		final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd.HHmmss z");
		final SimpleDateFormat dateFormatNoTz = new SimpleDateFormat("yyyy-MM-dd.HHmmss");
		
		try
		{
			// Get the text representing the date
			String dateText = conversationFile.getName().substring(0, conversationFile.getName().lastIndexOf('.'));
			
			if (dateText.length() > 17) // timezone
			{
				// Rearrange the timezone text
				dateText = dateText.substring(0, 17) + " " + dateText.substring(22) + 
					dateText.substring(17,20) + ":" + dateText.substring(20,22);
				
				return dateFormat.parse(dateText);
			}
			else // no timezone
			{
				return dateFormatNoTz.parse(dateText);
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Unrecognized conversation date format in filename");
		}
	}
	
	/**
	 * Interprets a GAIM service string.
	 * 
	 * @param serviceName The service name as it appears in the header
	 * @return The corresponding IMService constant
	 */
	protected static IMService _interpretService(String serviceName)
	{
		if (serviceName.equals("yahoo")) return IMService.YAHOO;
		if (serviceName.equals("msn")) return IMService.MSN;
		if (serviceName.equals("jabber")) return IMService.GTALK;
		
		throw new RuntimeException("Unrecognized service: '"+serviceName+"'");	
	}
}
