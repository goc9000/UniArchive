/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.json.JSONWriter;

import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.models.archive.IMArchive.Conversation.Reply;
import uniarchive.models.archive.IMArchive.Conversation.Speaker;

/**
 * A helper object for saving an IMArchive (or parts of it)
 * in JSON format to a file or through a writer.
 */
public class IMArchiveJsonWriter
{
	protected BufferedWriter _underWriter;
	protected JSONWriter _writer;
	
	/**
	 * Constructor for writing a standalone archive
	 * to a new file.
	 * 
	 * @param file The file to which the archive is to
	 *             be written
	 */
	public IMArchiveJsonWriter(File file) throws IOException
	{
		this(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
	}
	
	/**
	 * Constructor for writing archive data to an
	 * already created Writer.
	 * 
	 * @param writer A writer to which the data is
	 *               to be sent. Note that the writer
	 *               should be configured for UTF-8
	 *               writing.
	 */
	public IMArchiveJsonWriter(Writer writer)
	{
		this._underWriter = new BufferedWriter(writer);
		this._writer = new JSONWriter(this._underWriter);
	}
	
	/**
	 * Constructor for writing archive data to an
	 * already created JSONWriter. Note that if this
	 * constructor is used, you will be unable to
	 * flush or close the writer from this object,
	 * as the JSONWriter does not offer access to
	 * its underlying stream.
	 * 
	 * @param writer A writer to which the data is
	 *               to be sent
	 */
	public IMArchiveJsonWriter(JSONWriter writer)
	{
		this._underWriter = null;
		this._writer = writer;
	}
	
	/**
	 * Flushes the writing stream.
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException
	{
		if (this._underWriter != null) this._underWriter.flush();
	}
	
	/**
	 * Closes the writing stream.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException
	{
		if (this._underWriter != null) this._underWriter.close();
	}
	
	/**
	 * Writes an archive in JSON format.
	 * 
	 * @param archive The IM archive to write
	 * @param listener An object that will be notified of any progress
	 *                 in writing the archive. May be null.
	 */
	public void writeArchive(IMArchive archive, ProgressListener listener) throws Exception
	{
		if (listener != null) listener.onProgress(new ProgressEvent("Saving archive...", 0, -1));
		
		this._writer.object();
		
		// Write identities
		this._writer.key("identities").array();
		for (Contact identity : archive.getIdentitiesGroup().getContacts()) this.writeContact(identity);
		this._writer.endArray();
		
		// Write groups
		this._writer.key("groups").array();
		for (Group group : archive.getRegularGroups()) this.writeGroup(group);
		this._writer.endArray();
		
		// Writer conversations
		List<Conversation> conversations = archive.getConversations();
		int processed = 0;
		int total = conversations.size();
		this._writer.key("conversationsCount").value(total);
		this._writer.key("conversations").array();
		if (listener != null) listener.onProgress(new ProgressEvent("Saving archive...", processed, total));
		for (Conversation conv : conversations)
		{
			this.writeConversation(conv);
			processed++;
			if (listener != null) listener.onProgress(new ProgressEvent("Saving archive...", processed, total));
		}
		this._writer.endArray();
		
		this._writer.endObject();
	}
	
	/**
	 * Writes a group in JSON format, contacts and accounts
	 * included.
	 * 
	 * @param group The group to write
	 */
	public void writeGroup(Group group) throws Exception
	{
		this._writer.object();
		this._writer.key("name").value(group.name);
		
		// Write contacts
		this._writer.key("contacts").array();
		for (Contact contact : group.getContacts()) this.writeContact(contact);
		this._writer.endArray();
		
		this._writer.endObject();
	}
	
	/**
	 * Writes a contact in JSON format, accounts included.
	 * 
	 * @param contact The contact to write
	 */
	public void writeContact(Contact contact) throws Exception
	{
		this._writer.object();
		this._writer.key("name").value(contact.name);
		
		// Write accounts
		this._writer.key("accounts").array();
		for (Account account : contact.getAccounts()) this.writeAccount(account);
		this._writer.endArray();
		
		this._writer.endObject();
	}
	
	/**
	 * Writes an account in JSON format.
	 * 
	 * @param account The account to write
	 */
	public void writeAccount(Account account) throws Exception
	{
		this._writer.object();
		this._writer.key("id").value(this._getAccountId(account));
		this._writer.key("service").value(account.service.shortName);
		this._writer.key("name").value(account.name);
		this._writer.endObject();
	}
	
	/**
	 * Writes a conversation in JSON format.
	 * 
	 * @param conv The conversation to write
	 */
	public void writeConversation(Conversation conv) throws Exception
	{
		this._writer.object();
		this._writer.key("dateStarted").value(this._dateToStr(conv.dateStarted));
		this._writer.key("localAccountId").value(this._getAccountId(conv.localAccount));
		this._writer.key("remoteAccountId").value(this._getAccountId(conv.remoteAccount));
		this._writer.key("isConference").value(conv.isConference);
		
		// Write speakers
		this._writer.key("speakers").array();
		for (Speaker speaker : conv.getSpeakers()) this.writeSpeaker(speaker);
		this._writer.endArray();
		
		// Write replies
		this._writer.key("replies").array();
		for (Reply reply : conv.getReplies()) this.writeReply(reply);
		this._writer.endArray();
		
		this._writer.endObject();
	}
	
	/**
	 * Writes a speaker in JSON format.
	 * 
	 * @param speaker The speaker to write
	 */
	public void writeSpeaker(Speaker speaker) throws Exception
	{
		this._writer.object();
		this._writer.key("name").value(speaker.name);
		this._writer.key("accountId").value(this._getAccountId(speaker.account));
		this._writer.endObject();
	}
	
	/**
	 * Writes a reply in JSON format.
	 * 
	 * @param reply The reply to write
	 */
	public void writeReply(Reply reply) throws Exception
	{
		this._writer.object();
		this._writer.key("date").value(this._dateToStr(reply.date));
		this._writer.key("speaker").value(reply.speaker == null ? 0 : reply.speaker.getIndex()+1);
		this._writer.key("text").value(reply.text);
		this._writer.endObject();
	}
	
	/**
	 * Gets a textual identifier for an account.
	 * 
	 * @param account An account
	 * @return A unique account ID
	 */
	protected String _getAccountId(Account account)
	{
		return account.service.shortName+":"+account.name;
	}
	
	/**
	 * Converts a date to string format.
	 * 
	 * @param date A date
	 * @return A string representing the date in a MySQL-like
	 *         format, with millisecond resolution 
	 */
	protected String _dateToStr(Date date)
	{
		final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSS");
		
		return format.format(date);
	}
}
