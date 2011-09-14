/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.import_common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uniarchive.models.archive.FreeAccount;

/**
 * This class represents a query from the import process
 * asking the user to manually resolve a number of aliases.
 */
public class UnresolvedAliasesQuery extends Feedback
{
	public final List<Alias> aliases;
	public final List<FreeAccount> accounts;
	
	public UnresolvedAliasesQuery(List<Alias> aliases, List<FreeAccount> accounts)
	{
		this.aliases = new ArrayList<Alias>(aliases);
		Collections.sort(this.aliases);
		this.accounts = new ArrayList<FreeAccount>(accounts);
	}
	
	public UnresolvedAliasesQuery applyAnswer(List<Alias> resolution)
	{
		List<FreeAccount> newAccounts = new ArrayList<FreeAccount>(this.accounts);
		for (Alias alias : resolution)
			if ((alias.resolution != null) && !newAccounts.contains(alias.resolution))
				newAccounts.add(alias.resolution);
		
		return new UnresolvedAliasesQuery(resolution, newAccounts);
	}
}
