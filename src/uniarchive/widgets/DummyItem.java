/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import javax.swing.ImageIcon;

/**
 * Class for storing a "dummy item" in lists and other
 * controls (for instance, the 'at the end' virtual
 * group in the 'move group before' dialog)
 */
public class DummyItem
{
	public String name;
	public ImageIcon icon;
	
	/**
	 * Constructor.
	 * 
	 * @param name The displayed name for the dummy item
	 * @param icon The icon of the dummy item (may be null)
	 */
	public DummyItem(String name, ImageIcon icon)
	{
		this.name = name;
		this.icon = icon;
	}
}
