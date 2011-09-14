/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import uniarchive.graphics.IconManager;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for a label that displays an archive item
 * or a summary of a list of archive items.
 */
public class ArchiveItemDisplay extends JLabel
{
	private static final long serialVersionUID = 1L;
	
	protected static enum ItemType { NONE, GROUP, CONTACT, IDENTITY, ACCOUNT, UNDEFINED, MIXED };
	
	protected ImageIcon _accountIcon = IconManager.getInstance().getIcon("account");
	protected ImageIcon _contactIcon = IconManager.getInstance().getIcon("contact");
	protected ImageIcon _groupIcon = IconManager.getInstance().getIcon("group");
	protected ImageIcon _identityIcon = IconManager.getInstance().getIcon("identity");
	
	/**
	 * Constructor.
	 */
	public ArchiveItemDisplay()
	{
		this.setIcon(null);
		this.setText(" ");
	}
	
	/**
	 * Formats this field so as to show a representation
	 * of a given archive item.
	 * 
	 * @param item An archive item
	 */
	public void displayItem(Object item)
	{
		ImageIcon icon = null;
		String text = "???";
		
		switch (this._getItemType(item))
		{
		case NONE: icon = null; text = "Nothing"; break;
		case GROUP: icon = this._groupIcon; text = ((Group)item).name; break;
		case IDENTITY: icon = this._identityIcon; text = ((Contact)item).name; break;
		case CONTACT: icon = this._contactIcon; text = ((Contact)item).name; break;
		case ACCOUNT: icon = ((Account)item).service.icon; text = ((Account)item).name; break;
		case UNDEFINED: icon = null; text = "Item"; break; 
		}
		
		// Commit values
		this.setIcon(icon);
		this.setText(text);
	}
	
	/**
	 * Formats this field so as to show a representation
	 * of a given list of archive items.
	 * 
	 * @param items A list of items
	 */
	public void displayItems(List<Object> items)
	{		
		// Handle the 'empty list' case
		if ((items == null) || items.isEmpty())
		{
			this.setIcon(null);
			this.setText("Nothing");
			return;
		}
		
		// Handle the 'single item' case
		if (items.size() == 1)
		{
			this.displayItem(items.get(0));
			return;
		}
		
		// Determine the common type of the items
		ItemType type = null;
		for (Object item : items)
		{
			ItemType myType = this._getItemType(item);
			
			if (myType != type)
			{
				if (type != null) { type = ItemType.MIXED; break; }
				type = myType;
			}
		}
		
		ImageIcon icon = null;
		String text = "???";
		
		switch (type)
		{
		case NONE: icon = null; text = "Nothing"; break;
		case GROUP: icon = this._groupIcon; text = items.size()+" groups"; break;
		case IDENTITY: icon = this._identityIcon; text = items.size()+" identities"; break;
		case CONTACT: icon = this._contactIcon; text = items.size()+" contacts"; break;
		case ACCOUNT: icon = this._accountIcon; text = items.size()+" accounts"; break;
		case UNDEFINED: icon = null; text = items.size()+" items"; break; 
		}
		
		// Commit values
		this.setIcon(icon);
		this.setText(text);
	}

	/**
	 * Gets the internal type of an archive item.
	 * 
	 * @param item An archive item
	 * @return The item type, as an internal ItemType constant
	 */
	protected ItemType _getItemType(Object item)
	{
		if (item == null) return ItemType.NONE;
		if (item instanceof Group) return ItemType.GROUP;
		if (item instanceof Contact) return ((Contact)item).isIdentity() ? ItemType.IDENTITY : ItemType.CONTACT;
		if (item instanceof Account) return ItemType.ACCOUNT;
		if (item instanceof Account) return ItemType.ACCOUNT;
		
		return ItemType.UNDEFINED;
	}
}
