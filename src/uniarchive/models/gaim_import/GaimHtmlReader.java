/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import net.htmlparser.jericho.CharacterReference;
import net.htmlparser.jericho.EndTag;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.StreamedSource;
import net.htmlparser.jericho.Tag;

/**
 * GaimFileReader implementation for HTML conversation files.
 * This basically skips all HTML tags and converts BR tags
 * to newlines, while preserving the newlines that are in the
 * document.
 */
public class GaimHtmlReader extends GaimFileReader
{
	protected StreamedSource _lexer;
	protected Iterator<Segment> _lexerIterator;
	protected StringBuilder _lineBuf;
	protected int _cursor;
	protected int _bodyLevel;
	protected String _likelyEol;
	
	/**
	 * Constructor.
	 * 
	 * @param file The conversation file to be read
	 */
	public GaimHtmlReader(File file) throws Exception
	{
		super(file);
		
		this._lexer = new StreamedSource(new BufferedInputStream(new FileInputStream(file)));
		this._lineBuf = new StringBuilder();
		this._cursor = 0;
		this._lexerIterator = this._lexer.iterator();
		this._bodyLevel = 0;
		this._likelyEol = null;
	}
	
	@Override
	protected String _reallyReadLine() throws Exception
	{
		// Read from the file if the buffer is empty
		if (this._cursor >= this._lineBuf.length())
		{
			if (!this._readHtmlLine()) return null;
			
			// Try to guess the EOL sequence
			if (this._likelyEol == null) this._likelyEol = this._guessEol();
			
			// The first newline we read is likely an extraneous newline
			// pidgin adds to BR tags and such, therefore we will ignore it
			if ((this._likelyEol != null) && (this._lineBuf.indexOf(this._likelyEol) == 0))
				this._cursor += this._likelyEol.length();
		}
		
		// Otherwise, look for a newline (there could be multiple
		// lines in the buffer) starting from the cursor
		if (this._likelyEol != null)
		{
			int index = this._lineBuf.indexOf(this._likelyEol, this._cursor);
			
			if (index >= 0)
			{
				String line = this._lineBuf.substring(this._cursor, index);
				this._cursor = index + this._likelyEol.length();
				return line;
			}
		}
		
		// By now there is likely a single line in the buffer,
		// so just return that.
		String line = this._lineBuf.substring(this._cursor);
		this._cursor = this._lineBuf.length();
		return line;
	}
	
	/**
	 * Reads from the underlying HTML source into the
	 * buffer, up to the first HTML tag that implies
	 * a newline, i.e. BR, /P, /Hx, or the last BODY
	 * tag.
	 * 
	 * @return True if a line has been read, false
	 *         if there are no more lines
	 */
	protected boolean _readHtmlLine() throws Exception
	{
		this._lineBuf.setLength(0);
		this._cursor = 0;
		boolean found = false;
		
		while (this._lexerIterator.hasNext())
		{
			Segment seg = this._lexerIterator.next();
			
			if (seg instanceof Tag)
			{
				Tag asTag = (Tag)seg;
				String tagName = asTag.getName().toLowerCase();
				
				if (tagName.equals("body"))
					this._bodyLevel += (asTag instanceof EndTag) ? -1 : 1;
				
				if (tagName.equals("br") || (
						((asTag instanceof EndTag) && (
								tagName.equals("p") ||
								((tagName.compareTo("h1") >= 0) && (tagName.compareTo("h8") <= 0))))))
				{
					found = true;
					break;
				}
			}
			else if (seg instanceof CharacterReference)
			{
				if (this._bodyLevel > 0) ((CharacterReference)seg).appendCharTo(this._lineBuf);
			}
			else
			{
				if (this._bodyLevel > 0) this._lineBuf.append(seg.toString());
			}
		}
		
		return (this._lineBuf.length() > 0) || found;
	}
	
	/**
	 * Analyzes the buffer and attempts to guess the
	 * likely end-of-line sequence for the file.
	 * 
	 * @param text A snippet of text, given as a CharSequence
	 * @return The guessed EOL sequence, or null if there is
	 *         not enough information.
	 */
	protected String _guessEol()
	{
		if (this._lineBuf.indexOf("\r\n") >= 0) return "\r\n"; // Windows
		
		boolean haveLf = (this._lineBuf.indexOf("\n") >= 0);
		boolean haveCr = (this._lineBuf.indexOf("\r") >= 0);
		
		if (haveCr && !haveLf) return "\r"; // Mac
		if (haveLf) return "\n"; // Linux
		
		return null;
	}
}
