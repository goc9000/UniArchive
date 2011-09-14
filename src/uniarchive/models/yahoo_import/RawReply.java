/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.yahoo_import;

import java.util.Date;

/**
 * Class for storing a "raw" reply read from a Yahoo archive.
 */
public class RawReply
{	
	public static enum Type {
		REGULAR, START_CONV, SYSTEM, CONFERENCE_JOIN, CONFERENCE_LEAVE, CONFERENCE_DECLINE };
	
	public Type type;
	public Date date;
	public String sender;
	public String text;
	public Object extra;
	
	/**
	 * Constructor.
	 * 
	 * @param type The type of the reply
	 * @param date The reply date
	 * @param sender The reply sender name
	 * @param text The reply text
	 * @param extra An extra parameter 
	 */
	public RawReply(Type type, Date date, String sender, String text, Object extra)
	{
		this.type = type;
		this.date = date;
		this.sender = sender;
		this.text = text;
		this.extra = extra;
	}
}

