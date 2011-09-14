/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.msn_import;

import java.util.Date;

/**
 * Class for storing a "raw" reply read from a MSN archive.
 */
public class RawReply
{	
	public static enum Type {
		REGULAR, SYSTEM, CONFERENCE_JOIN, CONFERENCE_LEAVE };
	
	public Type type;
	public Date date;
	public String sender;
	public String receiver;
	public String text;
	public int sessionId;
	
	/**
	 * Constructor.
	 * 
	 * @param type The type of the reply
	 * @param date The reply date
	 * @param sender The reply sender name
	 * @param receiver The reply receiver name
	 * @param text The reply text 
	 * @param sessionId The ID for this reply's session
	 */
	public RawReply(Type type, Date date, String sender, String receiver, String text, int sessionId)
	{
		this.type = type;
		this.date = date;
		this.sender = sender;
		this.receiver = receiver;
		this.text = text;
		this.sessionId = sessionId;
	}
}
