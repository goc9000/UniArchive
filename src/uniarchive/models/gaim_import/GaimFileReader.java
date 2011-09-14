/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.File;

/**
 * Class for an object that reads both plaintext and
 * HTML Pidgin/Gaim conversations as sequences of lines
 * (HTML data is stripped of tags and unescaped), while also
 * providing a facility for "pushing back" the last read line.
 */
public abstract class GaimFileReader
{
	protected String _lastLine = null;
	protected boolean _useLast = false;
	
	/**
	 * Constructor.
	 * 
	 * @param file The conversation file to read
	 */
	public GaimFileReader(File file) throws Exception
	{
	}
	
	/**
	 * Reads the next line from the conversation file.
	 * 
	 * @return The next line in the file, or null if
	 *         there are no more lines.
	 */
	public String readLine()
	{
		if (this._useLast)
		{
			this._useLast = false;
			return this._lastLine;
		}
		
		try
		{
			this._lastLine = _reallyReadLine();
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot read from conversation file:\n"+e.getMessage());
		}
		
		return this._lastLine;
	}
	
	/**
	 * Undoes the reading of the last line, so
	 * that it will be returned again when
	 * readLine() is next called.
	 */
	public void pushBackLine()
	{
		this._useLast = true;
	}
	
	/**
	 * Actually reads a line from the underlying
	 * plaintext or HTML reader.
	 * 
	 * @return The next line in the file, or null if
	 *         there are no more lines.
	 */
	protected abstract String _reallyReadLine() throws Exception;
	
	/**
	 * Returns the appropriate type of reader (HTML or plaintext)
	 * for reading a given GAIM conversation file.
	 * 
	 * @param file A conversation file
	 * @return A new GAIM file reader for the file 
	 */
	public static GaimFileReader forFile(File file) throws Exception
	{
		String extension = file.getName().substring(file.getName().lastIndexOf('.')).toLowerCase();
		boolean isHtml = (extension.equals(".html") || extension.equals(".htm"));
		
		return isHtml ? new GaimHtmlReader(file) : new GaimPlaintextReader(file);
	}
}
