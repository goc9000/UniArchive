/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;
import uniarchive.models.archive.IMArchive.Conversation.Speaker;

/**
 * A helper object for loading an IMArchive (or parts of it)
 * in JSON format from a file or through a writer.
 */
public class IMArchiveJsonReader
{
	protected JSONTokener _reader;
	
	/**
	 * Constructor for reading a standalone archive
	 * from file.
	 * 
	 * @param file The file containing the archive
	 */
	public IMArchiveJsonReader(File file) throws IOException
	{
		this(new InputStreamReader(new FileInputStream(file), "UTF-8"));
	}
	
	/**
	 * Constructor for reading archive data from
	 * a Reader.
	 * 
	 * @param reader A reader from which the archive is
	 *               to be read. It should be configured
	 *               for the UTF-8 charset.
	 */
	public IMArchiveJsonReader(Reader reader)
	{
		this._reader = new JSONTokener(reader);
	}
	
	/**
	 * Constructor for reading archive data to an
	 * already created JSONTokener.
	 * 
	 * @param tokener A tokener from which archive
	 *                data is to be read.
	 */
	public IMArchiveJsonReader(JSONTokener tokener)
	{
		this._reader = tokener;
	}
	
	/**
	 * Reads an archive.
	 * 
	 * @param listener An entity that will be notified of any
	 *                 progress in reading the archive. May be
	 *                 null.
	 * @return The read archive
	 */
	public IMArchive readArchive(ProgressListener listener) throws Exception
	{
		IMArchive archive = new IMArchive();
		int total = -1;
	
		if (listener != null) listener.onProgress(new ProgressEvent("Loading archive...", 0, -1));
		
		// Note: the master archive object must be tokenized
		// manually, as it would be difficult to fit into memory
		// if it were read all at once.
	
		if (this._reader.nextClean() != '{') throw this._reader.syntaxError("Expecting '{'");
		
		while (true)
		{
			String key = this._reader.nextValue().toString();
			
			if (this._reader.nextClean() != ':') throw this._reader.syntaxError("Expecting ':'");
			
			if (key.equals("identities"))
			{
				Object data = this._reader.nextValue();
				if (!(data instanceof JSONArray)) throw this._reader.syntaxError("Expecting array of identity objects");
				JSONArray array = (JSONArray)data;
				for (int i=0; i<array.length(); i++) this.loadIdentity(archive, array.getJSONObject(i));
			}
			else if (key.equals("groups"))
			{
				Object data = this._reader.nextValue();
				if (!(data instanceof JSONArray)) throw this._reader.syntaxError("Expecting array of group objects");
				JSONArray array = (JSONArray)data;
				for (int i=0; i<array.length(); i++) this.loadGroup(archive, array.getJSONObject(i));
			}
			else if (key.equals("conversationsCount"))
			{
				Object data = this._reader.nextValue();
				if (!(data instanceof Number)) throw this._reader.syntaxError("Expecting number for conversation count");
				total = ((Number)data).intValue();
			}
			else if (key.equals("conversations"))
			{
				if (this._reader.nextClean() != '[') throw this._reader.syntaxError("Expecting '['");
				
				int processed = 0;
				if (listener != null) listener.onProgress(new ProgressEvent("Loading archive...", processed, total));
				
				if (this._reader.nextClean() != ']')
				{
					this._reader.back();
					while (true)
					{
						Object data = this._reader.nextValue();
						if (!(data instanceof JSONObject)) throw new RuntimeException("Expecting conversation object");
						
						this.loadConversation(archive, (JSONObject)data);
						
						char c = this._reader.nextClean();
						if (c == ']') break;
						if (c != ',') throw this._reader.syntaxError("Expecting ',' or ']'");
						
						processed++;
						if (listener != null) listener.onProgress(new ProgressEvent("Loading archive...", processed, total));
					}
				}
			}
			else
			{
				throw this._reader.syntaxError("Unsupported key '"+key+"'");
			}
			
			char c = this._reader.nextClean();
			if (c == '}') break;
			if (c != ',') throw this._reader.syntaxError("Expecting ',' or '}'");
		}
		
		return archive;
	}
	
	/**
	 * Decodes and loads a JSON object that represents an identity.
	 * 
	 * @param archive The archive that is to receive the identity
	 * @param identObj The JSON object that encodes the identity
	 */
	public void loadIdentity(IMArchive archive, JSONObject identObj) throws Exception
	{
		String name = identObj.getString("name");
		JSONArray accountsArr = identObj.getJSONArray("accounts");
		
		// Sanity check
		if (archive.getContactByName(name) != null) throw new RuntimeException("Duplicate identity '"+name+"'");
		
		Contact identity = archive.createIdentity(name);
		for (int i=0; i<accountsArr.length(); i++) this.loadAccount(identity, accountsArr.getJSONObject(i));
	}
	
	/**
	 * Decodes and loads a JSON object that represents a group.
	 * 
	 * @param archive The archive that is to receive the group
	 * @param groupObj The JSON object that encodes the group
	 */
	public void loadGroup(IMArchive archive, JSONObject groupObj) throws Exception
	{
		String name = groupObj.getString("name");
		JSONArray contactsArr = groupObj.getJSONArray("contacts");
		
		// Sanity check
		if (archive.getGroupByName(name) != null) throw new RuntimeException("Duplicate group '"+name+"'");
		
		Group group = archive.createGroup(name);
		for (int i=0; i<contactsArr.length(); i++) this.loadContact(group, contactsArr.getJSONObject(i));
	}
	
	/**
	 * Decodes and loads a JSON object that represents a contact.
	 * 
	 * @param group The group that is to receive the contact
	 * @param contactObj The JSON object that encodes the contact
	 */
	public void loadContact(Group group, JSONObject contactObj) throws Exception
	{
		String name = contactObj.getString("name");
		JSONArray accountsArr = contactObj.getJSONArray("accounts");
		
		// Sanity check
		if (group.getArchive().getContactByName(name) != null) throw new RuntimeException("Duplicate contact '"+name+"'");
		
		Contact contact = group.createContact(name);
		for (int i=0; i<accountsArr.length(); i++) this.loadAccount(contact, accountsArr.getJSONObject(i));
	}
	
	/**
	 * Decodes and loads a JSON object that represents an account.
	 * 
	 * @param contact The contact/identity that is to receive the account
	 * @param accountObj The JSON object that encodes the account
	 */
	public void loadAccount(Contact contact, JSONObject accountObj) throws Exception
	{
		IMService service = IMService.fromShortName(accountObj.getString("service"));
		String name = accountObj.getString("name");
		
		// Sanity check
		if (contact.getArchive().getAccountByName(service, name) != null)
			throw new RuntimeException("Duplicate account '"+service+":"+name+"'");
		
		contact.createAccount(service, name);
	}
	
	/**
	 * Decodes and loads a JSON object that represents a conversation.
	 * 
	 * @param archive The archive that is to receive the conversation
	 * @param convObj The JSON object that encodes the conversation
	 */
	public void loadConversation(IMArchive archive, JSONObject convObj) throws Exception
	{
		Date dateStarted = this._strToDate(convObj.getString("dateStarted"));
		Account localAccount = this._getAccountForId(archive, convObj.getString("localAccountId"));
		Account remoteAccount = this._getAccountForId(archive, convObj.getString("remoteAccountId"));
		boolean isConference = convObj.getBoolean("isConference");
		JSONArray speakerArr = convObj.getJSONArray("speakers");
		JSONArray repliesArr = convObj.getJSONArray("replies");
		
		Conversation conv = archive.createConversation(dateStarted, localAccount, remoteAccount, isConference);
		for (int i=0; i<speakerArr.length(); i++) this.loadSpeaker(conv, speakerArr.getJSONObject(i));
		for (int i=0; i<repliesArr.length(); i++) this.loadReply(conv, repliesArr.getJSONObject(i));
	}
	
	/**
	 * Decodes and loads a JSON object that represents a speaker.
	 * 
	 * @param conversation The conversation that is to receive the speaker
	 * @param speakerObj The JSON object that encodes the speaker
	 */
	public void loadSpeaker(Conversation conversation, JSONObject speakerObj) throws Exception
	{
		String name = speakerObj.getString("name");
		Account account = this._getAccountForId(conversation.getArchive(), speakerObj.getString("accountId"));
		
		// Sanity check
		if (conversation.getSpeakerByName(name) != null) throw new RuntimeException("Duplicate speaker '"+name+"'");
		
		conversation.addSpeaker(name, account);
	}
	
	/**
	 * Decodes and loads a JSON object that represents a reply.
	 * 
	 * @param conversation The conversation that is to receive the reply
	 * @param replyObj The JSON object that encodes the reply
	 */
	public void loadReply(Conversation conversation, JSONObject replyObj) throws Exception
	{
		Date date = this._strToDate(replyObj.getString("date"));
		int speakerId = replyObj.getInt("speaker");
		String content = replyObj.getString("text");
		
		// Sanity check
		if ((speakerId < 0) || (speakerId > conversation.getSpeakers().size()))
			throw new RuntimeException("Invalid speaker ID");
		
		Speaker speaker = (speakerId != 0) ? conversation.getSpeakers().get(speakerId-1) : null;

		conversation.addReply(date, speaker, content);
	}
	
	/**
	 * Retrieves an account referenced by an ID string in the
	 * JSON file.
	 * 
	 * @param archive The archive containing all the accounts
	 * @param accountId An account ID
	 * @return The account referenced by the ID
	 */
	protected Account _getAccountForId(IMArchive archive, String accountId)
	{
		int pos = accountId.indexOf(":");
		
		Account account = archive.getAccountByName(IMService.fromShortName(accountId.substring(0,pos)), accountId.substring(pos+1));
		if (account == null) throw new RuntimeException("Cannot find the '"+accountId+"' account in the archive");
		
		return account;
	}
	
	/**
	 * Retrieves a date value stored as a string.
	 * 
	 * @param dateStr A string representing a date, in a MySql-like format
	 * @return The decoded date
	 */
	protected Date _strToDate(String dateStr)
	{
		final SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSS");
		final SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		try { return format1.parse(dateStr); }
		catch (Exception e)
		{
			try { return format2.parse(dateStr); }
			catch (Exception e2)
			{
				throw new RuntimeException("Cannot parse date '"+dateStr+"'");
			}
		}
	}
}
