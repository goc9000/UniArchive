/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for reading replies in a Gaim conversation.
 */
public class GaimConversationReader implements Iterator<RawReply>
{
	protected File _convFile;
	protected Date _initBaseDate;

	protected GaimFileReader _reader;
	protected Date _baseDate;
	protected Date _prevReplyDate;
	protected RawReply _bufferedReply;
	
	/**
	 * Constructor.
	 * 
	 * @param conversationFile The conversation file
	 * @param baseDate The base date for the replies (usually the
	 *                 conversation date is supplied here)
	 */
	public GaimConversationReader(File conversationFile, Date baseDate)
	{	
		this._convFile = conversationFile;
		this._initBaseDate = baseDate;
		
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
			this._reader = GaimFileReader.forFile(this._convFile);
			this._reader.readLine(); // skip header
			this._baseDate = this._initBaseDate;
			this._prevReplyDate = null;
			this._bufferedReply = null;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Cannot scan conversation for replies:\n"+e.getMessage());
		}
	}
	
	/**
	 * Reads a reply from the underlying conversation file reader.
	 * 
	 * @return An object containing the raw reply, or null if there
	 *         are no more replies
	 */
	protected RawReply _readReply()
	{
		final Pattern PAT_REPLY_1ST_LINE = Pattern.compile("^\\(([^)]*[0-9]+:[0-9]+:[0-9]+[^)]*)\\)\\s*(.*)$");
		final Pattern PAT_REPLY_CONTENT = Pattern.compile("^([\\s\\w.@()-]+)(?:/[^:]+)?: (.*)$");	
		
		final SimpleDateFormat[] REPLY_DATE_FORMATS = {
			new SimpleDateFormat("hh:mm:ss a"),
			new SimpleDateFormat("HH:mm:ss"),
			new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a"),
			new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
		};

		// Read the first line in a new reply
		String line = this._reader.readLine();
		if (line == null) return null;
		Matcher matcher = PAT_REPLY_1ST_LINE.matcher(line);
		if (!matcher.find()) throw new RuntimeException("Expecting start of reply, got '"+line+"'");
		
		String dateString = matcher.group(1);
		String restOfLine = matcher.group(2);
		
		// Decode the reply date
		Date replyDate = null;
		for (SimpleDateFormat dateFormat : REPLY_DATE_FORMATS)
		{
			try { replyDate = dateFormat.parse(dateString); } catch (Exception e) {}
			if (replyDate != null) break;
		}
		if (replyDate == null) throw new RuntimeException("Unrecognized reply date format: '"+dateString+"'");
		
		// If a date was not specified explicitly (as is usually the
		// case), fill in the fields using the base date (usually equal
		// to the conversation date)
		boolean partial = this._isPartialDate(replyDate);
		if (partial) replyDate = this._completeDate(replyDate);
		
		// Temporal anomaly: this reply occurs before the previous one;
		// This may occur during quoting, receiving offline messages,
		// 24-hour rollover, or may simply be unexplained.
		if (partial && (this._prevReplyDate != null) && replyDate.before(this._prevReplyDate))
		{
			// Handle the case where this occurs due to 24-hour rollover
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(replyDate);
			if (calendar.get(Calendar.HOUR_OF_DAY) == 0)
			{
				this._rollBaseDate();
				replyDate = this._completeDate(replyDate);
			}
			
			// Otherwise, leave it as it is; the replies will be kept
			// in sequence anyway
		}
		
		// Decode the sender and the reply text
		String replySender;
		StringBuilder replyText = new StringBuilder();
		
		matcher = PAT_REPLY_CONTENT.matcher(restOfLine);
		
		if (matcher.find() && !matcher.group(1).equalsIgnoreCase("unable to send message"))
		{
			// Normal reply
			replySender = matcher.group(1);
			replyText.append(matcher.group(2));
		}
		else
		{
			// System reply
			replySender = null;
			replyText.append(restOfLine);
		}
		
		// Read the continuation of the reply, if it is multiline
		while ((line = this._reader.readLine()) != null)
		{
			if (PAT_REPLY_1ST_LINE.matcher(line).find()) break;
			replyText.append("\n");
			replyText.append(line);
		}
		this._reader.pushBackLine();
		
		// Create the reply and return it
		this._prevReplyDate = replyDate;
		return new RawReply(replyDate, replySender, replyText.toString());
	}
	
	/**
	 * Checks whether a date is "partial", i.e. only the time is
	 * specified (as is usually the case with replies)
	 * 
	 * @param date The date to be tested
	 * @return True if the date is partial, false otherwise
	 */
	protected boolean _isPartialDate(Date date)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return (calendar.get(Calendar.YEAR) == 1970);
	}
	
	/**
	 * Fills in the year, month and day fields in a
	 * date using the base date.
	 * 
	 * @param date The date to be modified
	 * @return The modified date
	 */
	protected Date _completeDate(Date date)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(this._baseDate);
		
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH);
		int day = calendar.get(Calendar.DATE);
		
		calendar.setTime(date);
		calendar.set(year, month, day);
		return calendar.getTime();
	}
	
	/**
	 * Advances the base date by a day.
	 */
	protected void _rollBaseDate()
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(this._baseDate);
		calendar.roll(Calendar.DATE, true);
		this._baseDate = calendar.getTime();
	}
}