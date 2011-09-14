/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.digsby_import;

import java.util.Date;

/**
 * Class for storing a "raw" reply read from a Digsby archive.
 * 
 * Note: Digsby doesn't seem to support logging system messages yet.
 */
public class RawReply
{	
	public Date date;
	public String sender;
	public String text;
	
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
	}
}

