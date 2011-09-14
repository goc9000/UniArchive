/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.util.Collection;

import uniarchive.models.archive.FreeAccount;

/**
 * Class for storing info about an imported account.
 */
public class ImportedAccountInfo implements Comparable<ImportedAccountInfo>
{
	public final FreeAccount account;
	public final String[] aliases;
	public final boolean isLocal;
	
	/**
	 * Constructor.
	 * 
	 * @param account The imported account
	 * @param aliases A list of aliases found for this account
	 * @param isLocal Whether the account is local or not
	 */
	public ImportedAccountInfo(FreeAccount account, Collection<String> aliases, boolean isLocal)
	{
		this.account = account;
		this.aliases = aliases.toArray(new String[aliases.size()]);
		this.isLocal = isLocal;
	}

	@Override
	public int compareTo(ImportedAccountInfo other)
	{
		if (other.isLocal != this.isLocal) return this.isLocal ? -1 : 1;
		
		return this.account.compareTo(other.account);
	}
}
