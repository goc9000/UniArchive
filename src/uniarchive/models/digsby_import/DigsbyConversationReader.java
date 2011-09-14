/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.digsby_import;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import net.htmlparser.jericho.CharacterReference;
import net.htmlparser.jericho.EndTag;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StreamedSource;
import net.htmlparser.jericho.Tag;

/**
 * Class for reading replies in a Digsby conversation file.
 */
public class DigsbyConversationReader implements Iterator<RawReply>
{
	protected File _convFile;
	protected int _replyOffset;
	protected int _replyCount;
	
	protected StreamedSource _lexer;
	protected Iterator<Segment> _lexerIterator;
	protected StringBuilder _stringBuf = new StringBuilder();
	protected int _replyIndex = 0;
	protected RawReply _bufferedReply;
	
	/**
	 * Constructor.
	 * 
	 * @param conversationFile The conversation file
	 * @param replyOffset The reply index at which replies for this conversation
	 *                    begin in the underlying file
	 * @param replyCount The span of this conversation in the underlying file,
	 *                   in terms of replies
	 */
	public DigsbyConversationReader(File conversationFile, int replyOffset, int replyCount)
	{	
		this._convFile = conversationFile;
		this._replyOffset = replyOffset;
		this._replyCount = replyCount;
		
		this.reset();
	}
	
	@Override
	public boolean hasNext()
	{
		if (this._bufferedReply == null) this._bufferedReply = this._readReply();
		
		return (this._bufferedReply != null);
	}

	@Override
	public RawReply next()
	{
		if (this._bufferedReply == null) this._bufferedReply = this._readReply();
		
		RawReply reply = this._bufferedReply;
		this._bufferedReply = null;
		
		return reply;
	}

	/**
	 * This method is just here to fulfill the contract for
	 * Iterator. It throws an UnsupportedOperationException.
	 */
	public void remove()
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Rewinds the reader to the first reply.
	 */
	public void reset()
	{
		try
		{
			this._lexer = new StreamedSource(this._convFile.toURI().toURL());
			this._lexerIterator = this._lexer.iterator();
			this._replyIndex = 0;
			this._bufferedReply = null;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot scan conversation for replies:\n"+e.getMessage());
		}
	}
	
	/**
	 * Reads a reply from the underlying XML event reader.
	 * 
	 * @return An object containing the raw reply, or null if there
	 *         are no more replies
	 */
	protected RawReply _readReply()
	{
		final SimpleDateFormat timestampFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		final int NONE = Integer.MAX_VALUE;
		
		try
		{
			int divLevel = 0;
			int spanLevel = 0;
			
			int replyDivLevel = NONE;
			int buddySpanLevel = NONE;
			int contentSpanLevel = NONE;
			
			String buddy = null;
			String content = null;
			Date replyDate = null;
			
			this._stringBuf.setLength(0);
			
			while (this._lexerIterator.hasNext() &&
					((this._replyCount == Integer.MAX_VALUE) || (this._replyIndex < this._replyOffset+this._replyCount)))
			{
				Segment segment = this._lexerIterator.next();
				
				if (segment instanceof Tag)
				{
					String tagName = ((Tag)segment).getName().toLowerCase();
					
					// Handle opening tags
					if (segment instanceof StartTag)
					{
						StartTag asTag = (StartTag)segment;
						
						if (tagName.equals("br"))
						{
							if ((buddySpanLevel != NONE) || (contentSpanLevel != NONE))
								this._stringBuf.append("\n");
						}
						if (tagName.equals("div"))
						{
							divLevel++;
							
							String dateStr = asTag.getAttributeValue("timestamp");
							if (dateStr == null) throw new RuntimeException("Timestamp attribute not found");
							try
							{
								replyDate = timestampFmt.parse(dateStr);
							}
							catch (Exception e)
							{
								throw new RuntimeException("Invalid date format: "+dateStr);
							}
							buddy = null;
							content = null;
							
							if (replyDivLevel == NONE) replyDivLevel = divLevel;
						}
						else if (tagName.equals("span"))
						{
							spanLevel++;
							String spanType = asTag.getAttributeValue("class");
							
							if (("buddy".equals(spanType)) && (buddySpanLevel == NONE))
								buddySpanLevel = spanLevel;
							
							if (("msgcontent".equals(spanType)) && (contentSpanLevel == NONE))
								contentSpanLevel = spanLevel;
						}
					}
					
					// Handle closing tags
					if (segment instanceof EndTag)
					{
						if (tagName.equals("div"))
						{
							if (divLevel == replyDivLevel) // reply ends
							{
								if (this._replyIndex >= this._replyOffset)
								{
									this._replyIndex++;
									if (buddy == null) throw new RuntimeException("Buddy not specified");
									if (content == null) throw new RuntimeException("Content not specified");
									return new RawReply(replyDate, buddy, content);
								}
								
								this._replyIndex++;
								replyDivLevel = NONE;
							}
							divLevel--;
						}
						else if (tagName.equals("span"))
						{
							if (spanLevel == buddySpanLevel) // buddy ends
							{
								buddy = this._stringBuf.toString();
								this._stringBuf.setLength(0);
								buddySpanLevel = NONE;
							}
							else if (spanLevel == contentSpanLevel) // content ends
							{
								content = this._stringBuf.toString();
								this._stringBuf.setLength(0);
								contentSpanLevel = NONE;
							}
							spanLevel--;
						}
					}
				}
				else if (segment instanceof CharacterReference)
				{
					if ((buddySpanLevel != NONE) || (contentSpanLevel != NONE))
						((CharacterReference)segment).appendCharTo(this._stringBuf);
				}
				else
				{
					if ((buddySpanLevel != NONE) || (contentSpanLevel != NONE))
						this._stringBuf.append(segment.toString());
				}
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error reading replies:\n"+e.getMessage());
		}
		
		return null;
	}
}
