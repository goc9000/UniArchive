/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.import_common;

/**
 * This class represents a generic message issued by the
 * import process.
 */
public class ImportMessage extends Feedback
{
	public enum Type { INFORMATION, WARNING, ERROR };
	
	public Type type;
	public String content;
	
	/**
	 * Constructor.
	 * 
	 * @param type The message type
	 * @param content The message content
	 */
	public ImportMessage(Type type, String content)
	{
		this.type = type;
		this.content = content;
	}
}