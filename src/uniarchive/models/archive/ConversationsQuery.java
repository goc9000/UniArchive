/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for holding filtering and sorting information
 * for a query into the conversations list of an
 * archive.
 */
public class ConversationsQuery implements Cloneable
{
	public enum SortKey { BY_DATE, BY_CONTACT, BY_ACCOUNT, BY_TYPE };
	
	public List<Group> filterGroups;
	public List<Contact> filterContacts;
	public List<Account> filterAccounts;
	public List<Conversation> filterConversations;
	public List<SortKey> sortKeys;
	
	/**
	 * Constructor for a default query.
	 */
	public ConversationsQuery()
	{
		this.filterGroups = new ArrayList<Group>();
		this.filterContacts = new ArrayList<Contact>();
		this.filterAccounts = new ArrayList<Account>();
		this.filterConversations = new ArrayList<Conversation>();
		
		this.sortKeys = new ArrayList<SortKey>();
		this.sortKeys.add(SortKey.BY_DATE);
	}
	
	/**
	 * Clones this query.
	 * 
	 * @return A query with the same content.
	 */
	public ConversationsQuery clone()
	{
		ConversationsQuery cloned = new ConversationsQuery();
		cloned.filterGroups.addAll(this.filterGroups);
		cloned.filterContacts.addAll(this.filterContacts);
		cloned.filterAccounts.addAll(this.filterAccounts);
		cloned.filterConversations.addAll(this.filterConversations);
		
		return cloned;
	}
	
	/**
	 * Calculates a set of all the identity accounts spanned
	 * by this query's filter.
	 * 
	 * @return A set of accounts
	 */
	public Set<Account> getIdentityAccountsInFilter()
	{
		Set<Account> result = new TreeSet<Account>();
	
		// Handle groups; only the Identities group actually
		// matters, the others only affect regular accounts
		for (Group group : this.filterGroups)
			if (group.isIdentitiesGroup())
			{
				// Selecting the entire Indentities group is equivalent
				// to selecting no identity objects at all (i.e. everything
				// is returned)
				result.clear();
				return result;
			}
		
		// Handle identity contacts
		for (Contact contact : this.filterContacts)
			if (contact.isIdentity())
				result.addAll(contact.getAccounts());
	
		// Handle accounts
		for (Account account : this.filterAccounts)
			if (account.isIdentityAccount())
				result.add(account);
		
		return result;
	}
	
	/**
	 * Calculates a set of all the regular accounts spanned
	 * by this query's filter.
	 * 
	 * @return A set of accounts
	 */
	public Set<Account> getRegularAccountsInFilter()
	{
		Set<Account> result = new TreeSet<Account>();
	
		// Handle groups
		for (Group group : this.filterGroups)
			if (!group.isIdentitiesGroup())
			for (Contact contact : group.getContacts())
				result.addAll(contact.getAccounts());
		
		// Handle regular contacts
		for (Contact contact : this.filterContacts)
			if (!contact.isIdentity())
				result.addAll(contact.getAccounts());
	
		// Handle accounts
		for (Account account : this.filterAccounts)
			if (!account.isIdentityAccount())
				result.add(account);
		
		return result;
	}
}
