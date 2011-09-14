/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

/**
 * Class for modeling a free account, i.e. one that
 * is not tied to any archive. Such accounts can be
 * manipulated freely, but cannot be added directly
 * to an archive.
 */
public class FreeAccount implements Comparable<FreeAccount>
{
	public final IMService service;
	public final String name;
	
	/**
	 * Constructor.
	 * 
	 * @param service The IM service for this account
	 * @param name The account name within the specified service
	 */
	public FreeAccount(IMService service, String name)
	{
		this.service = service;
		this.name = name;
	}

	@Override
	public int compareTo(FreeAccount other)
	{
		if (other.service != this.service) return this.service.compareTo(other.service);
		
		return this.name.compareTo(other.name);
	}
	
	public boolean equals(FreeAccount other)
	{
		return (this.service == other.service) && (this.name.equals(other.name));
	}
	
	@Override
	public String toString()
	{
		return this.service.shortName+":"+this.name;
	}
}