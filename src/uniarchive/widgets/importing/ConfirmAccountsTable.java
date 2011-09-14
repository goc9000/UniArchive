/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets.importing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import uniarchive.models.archive.FreeAccount;
import uniarchive.models.gaim_import.ImportedAccountInfo;
import uniarchive.widgets.TableWithFillColumn;

/**
 * Class for an accounts table used in the "confirm new accounts"
 * page of the Gaim Import dialog.
 * 
 * Note that the table is read-only.
 */
public class ConfirmAccountsTable extends TableWithFillColumn
{
	private static final long serialVersionUID = 1L;
	
	/**
	 * Constructor.
	 */
	public ConfirmAccountsTable()
	{
		super();
		
		// Configure table properties
		this.setAutoCreateColumnsFromModel(false);
		this.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		this.setShowHorizontalLines(true);
		this.setShowVerticalLines(true);
		this.setGridColor(new Color(0.75f,0.75f,0.85f));
		this.setModel(new Model());
		
		// Create columns
		TableColumn accountColumn = new TableColumn(Model.COLUMN_INDEX_ACCOUNT);
		accountColumn.setPreferredWidth(this.getFont().getSize()*20);
		accountColumn.setCellRenderer(new AccountRenderer());
		
		TableColumn aliasesColumn = new TableColumn(Model.COLUMN_INDEX_ALIASES);
		aliasesColumn.setCellRenderer(new AliasesRenderer());
		
		// Add columns
		this.addColumn(accountColumn);
		this.addColumn(aliasesColumn);
		this.setFillColumn(aliasesColumn);
	}
	
	/**
	 * Loads data into the table.
	 * 
	 * @param accountInfo A list of accounts and associated info
	 */
	public void loadData(List<ImportedAccountInfo> accountInfo)
	{
		((Model)this.getModel()).loadData(accountInfo);
	}
	
	/**
	 * Internal class for rendering cells in this data
	 * table with special formatting for local accounts.
	 */
	protected static abstract class ARenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		protected Font _normalFont;
		protected Font _boldFont;
		protected Color _normalBkgd;
		protected Color _localBkgd = new Color(0.9f,0.95f,1.0f);
		
		/**
		 * Constructor.
		 */
		public ARenderer()
		{
			super();
			
			this._normalFont = this.getFont();
			this._boldFont = this._normalFont.deriveFont(Font.BOLD);
			this._normalBkgd = this.getBackground();
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, value.toString(), isSelected, hasFocus,
					row, column);
		
			ImportedAccountInfo accInfo = ((Model)table.getModel()).getRecord(row);
			
			if (!isSelected) renderer.setBackground(accInfo.isLocal ? this._localBkgd : this._normalBkgd);
			renderer.setFont((accInfo.aliases.length>1) ? this._boldFont : this._normalFont);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class for rendering an Account cell
	 * in this data table. 
	 */
	protected static class AccountRenderer extends ARenderer
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			FreeAccount account = (FreeAccount)value;
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, account.name, isSelected, hasFocus,
					row, column);
			
			renderer.setIcon(account.service.icon);
			renderer.setIconTextGap(6);
			
			return renderer;
		}
	}

	/**
	 * Internal class for rendering an Aliases cell
	 * in this data table. 
	 */
	protected static class AliasesRenderer extends ARenderer
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			StringBuffer buff = new StringBuffer();
			for (String name : (String[])value)
			{
				if (buff.length() > 0) buff.append(", ");
				buff.append(name);
			}
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, buff.toString(), isSelected, hasFocus,
					row, column);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class defining the model for the Accounts Table.
	 */
	protected static class Model extends AbstractTableModel
	{
		private static final long serialVersionUID = 1L;
		
		public static final int COLUMN_INDEX_ACCOUNT = 0;
		public static final int COLUMN_INDEX_ALIASES = 1;
		
		protected ImportedAccountInfo[] _data;
		
		/**
		 * Constructor.
		 */
		public Model()
		{
			this._data = new ImportedAccountInfo[0];	
		}
		
		/**
		 * Loads data into the model.
		 * 
		 * @param accountInfo A list of accounts and associated info
		 */
		public void loadData(List<ImportedAccountInfo> accountInfo)
		{
			this._data = accountInfo.toArray(new ImportedAccountInfo[accountInfo.size()]);
			this.fireTableDataChanged();
		}
		
		/**
		 * Returns the record at a given index.
		 * 
		 * @param index A zero-based record index
		 * @return A ImportedAccountInfo record
		 */
		public ImportedAccountInfo getRecord(int index)
		{
			return this._data[index];
		}
		
		@Override
		public int getColumnCount()
		{
			return 2;
		}

		@Override
		public int findColumn(String columnName)
		{
			return super.findColumn(columnName);
		}

		@Override
		public Class<?> getColumnClass(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ACCOUNT: return FreeAccount.class;
				case COLUMN_INDEX_ALIASES: return String[].class;
				default: return null;
			}
		}

		@Override
		public String getColumnName(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ACCOUNT: return "Account";
				case COLUMN_INDEX_ALIASES: return "Aliases";
				default: return "N/A";
			}
		}

		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}

		@Override
		public int getRowCount()
		{
			return this._data.length;
		}
		
		@Override
		public Object getValueAt(int row, int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ACCOUNT: return this._data[row].account;
				case COLUMN_INDEX_ALIASES: return this._data[row].aliases;
				default: return null;
			}
		}
	}
}
