/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * An improved JTable that allows the setting of a 'fill column', i.e.
 * a column that will attempt to absorb all size changes so that other
 * columns can remain at their preferred sizes. 
 * 
 * @author cristian
 */
public class TableWithFillColumn extends JTable
{
	private static final long serialVersionUID = 1L;
	
	protected TableColumn _fillColumn = null;
	
	public void setFillColumn(TableColumn fillColumn)
	{
		this._fillColumn = fillColumn;
	}
	
	@Override
	public void doLayout()
	{
		int spaceLeft = this.getWidth();
		TableColumnModel columns = this.getColumnModel();
		
		for (int i=0; i<columns.getColumnCount(); i++)
		{
			if (columns.getColumn(i) != this._fillColumn) spaceLeft -= columns.getColumn(i).getPreferredWidth();
		}
		
		this._fillColumn.setPreferredWidth(Math.max(0, spaceLeft));
		
		super.doLayout();
	}
}
