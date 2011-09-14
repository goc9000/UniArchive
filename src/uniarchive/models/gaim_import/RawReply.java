/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for storing a "raw" reply read from a GAIM archive.
 */
public class RawReply
{	
	public static enum Type { REGULAR, SYSTEM, NAME_CHANGE, CONFERENCE_JOIN };
	
	public Type type;
	public Date date;
	public String sender;
	public String text;
	public NameChangeData nameChangeData = null;
	public ConferenceJoinData conferenceJoinData = null;
	
	/**
	 * Constructor.
	 * 
	 * @param date The reply date
	 * @param sender The reply sender name
	 * @param text The reply text
	 */
	public RawReply(Date date, String sender, String text)
	{
		this.date = date;
		this.sender = sender;
		this.text = text;
		
		this._analyze();
	}
	
	/**
	 * Analyze this reply so as to determine its type and
	 * any type-specific info.
	 */
	protected void _analyze()
	{
		final Pattern PAT_NAME_CHANGE = Pattern.compile("^\\s*(.+) is now known as (.+)[.]\\s*$");
		final Pattern PAT_CONFERENCE_JOIN = Pattern.compile("^\\s*(.+) entered the room[.]\\s*$");
		Matcher matcher;
		this.type = Type.REGULAR;
		
		if (this.sender == null)
		{
			this.type = Type.SYSTEM;
			
			if ((matcher = PAT_NAME_CHANGE.matcher(this.text)).find())
			{
				this.type = Type.NAME_CHANGE;
				this.nameChangeData = new NameChangeData(matcher.group(1),matcher.group(2));
				return;
			}
			
			if ((matcher = PAT_CONFERENCE_JOIN.matcher(this.text)).find())
			{
				this.type = Type.CONFERENCE_JOIN;
				this.conferenceJoinData = new ConferenceJoinData(matcher.group(1));
				return;
			}
		}
	}
	
	/**
	 * Class for representing name change info in a
	 * "X is now known as Y" reply.
	 */
	public static class NameChangeData
	{
		public final String oldName;
		public final String newName;
		
		/**
		 * Constructor.
		 * 
		 * @param oldName The name to be changed
		 * @param newName The new name
		 */
		public NameChangeData(String oldName, String newName)
		{
			this.oldName = oldName;
			this.newName = newName;
		}
	}
	
	/**
	 * Class for representing conference join/part data in a
	 * "X entered/left the room" reply.
	 */
	public static class ConferenceJoinData
	{
		public final String name;
		
		/**
		 * Constructor.
		 * 
		 * @param name The name of the person joining/parting
		 */
		public ConferenceJoinData(String name)
		{
			this.name = name;
		}
	}
}
