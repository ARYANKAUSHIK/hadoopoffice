/**
* Copyright 2018 ZuInnoTe (Jörn Franke) <zuinnote@gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/
package org.zuinnote.flink.office.excel;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.common.io.CheckpointableInputFormat;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.Path;
import org.apache.flink.types.Row;
import org.zuinnote.flink.office.AbstractSpreadSheetFlinkFileInputFormat;
import org.zuinnote.hadoop.office.format.common.HadoopOfficeReadConfiguration;
import org.zuinnote.hadoop.office.format.common.converter.ExcelConverterSimpleSpreadSheetCellDAO;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericDataType;
import org.zuinnote.hadoop.office.format.common.dao.SpreadSheetCellDAO;

/**
 *
 *
 */
public class RowSimpleExcelFlinkFileInputFormat extends AbstractSpreadSheetFlinkFileInputFormat<Row>
implements CheckpointableInputFormat<FileInputSplit, Tuple3<Long, Long, GenericDataType[]>>,ResultTypeQueryable<Row> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -9124263750507448247L;
	private static final Log LOG = LogFactory.getLog(RowSimpleExcelFlinkFileInputFormat.class.getName());
	private long maxInferRows;
	private ExcelConverterSimpleSpreadSheetCellDAO converter;
	private HadoopOfficeReadConfiguration shocr;
	private GenericDataType[] customSchema;
	private TypeInformation[] fieldTypeInfos;
	
	public RowSimpleExcelFlinkFileInputFormat(HadoopOfficeReadConfiguration hocr, long maxInferRows,
			TypeInformation[] fieldTypeInfos) {
		super(hocr);
		this.maxInferRows = maxInferRows;
		this.shocr = hocr;

		this.converter = new ExcelConverterSimpleSpreadSheetCellDAO(this.shocr.getSimpleDateFormat(), this.shocr.getSimpleDecimalFormat(),this.shocr.getSimpleDateTimeFormat());
		hocr.setMimeType(AbstractSpreadSheetFlinkFileInputFormat.MIMETYPE_EXCEL);
		this.fieldTypeInfos=fieldTypeInfos;
	}
	
	/***
	 * Get the inferred schema of the underlying data
	 * 
	 * @return
	 */

	public GenericDataType[] getInferredSchema() {
		return this.converter.getSchemaRow();
	}

	/***
	 * Get custom schema defined for converting data from Excel to primitive
	 * datatypes
	 * 
	 * @return
	 */
	public GenericDataType[] getSchema() {
		return this.converter.getSchemaRow();
	}

	/**
	 * Set a custom schema used for converting data from Excel to primitive
	 * datatypes
	 * 
	 * @param customSchema
	 */
	public void setSchema(GenericDataType[] customSchema) {
		this.customSchema = customSchema;
	}

	/**
	 * Open an Excel file
	 * 
	 * @param split
	 *            contains the Excel file
	 */
	@Override
	public void open(FileInputSplit split) throws IOException {
		// read Excel
		super.open(split);
		// infer schema (requires to read file again)
		if (this.customSchema == null) {
			ExcelFlinkFileInputFormat effif = new ExcelFlinkFileInputFormat(this.shocr);
			effif.open(split);
			SpreadSheetCellDAO[] currentRow = effif.nextRecord(null);
			int i = 0;
			while ((currentRow != null) && (i != this.maxInferRows)) {
				this.converter.updateSpreadSheetCellRowToInferSchemaInformation(currentRow);
				i++;
				currentRow = effif.nextRecord(null);
			}
			effif.close();
			this.customSchema = this.converter.getSchemaRow();
		} else {
			this.converter.setSchemaRow(this.customSchema);
		}
	}

	@Override
	public Row nextRecord(Row reuse) throws IOException {
		SpreadSheetCellDAO[] nextRow = (SpreadSheetCellDAO[]) this.readNextRow();
		if (nextRow == null) {
			return null;
		}
		Object[] convertedRow = this.converter.getDataAccordingToSchema(nextRow);
		Row reuseRow;
		if (reuse==null) {
			reuseRow = new Row(this.customSchema.length);
		}  else {
			reuseRow=reuse;
		}
		for (int i=0;i<convertedRow.length;i++) {
			if (convertedRow[i] instanceof Date) {
				convertedRow[i]=new java.sql.Date(((Date)convertedRow[i]).getTime());
			}
			reuseRow.setField(i, convertedRow[i]);
		}
		return reuseRow;

	}

	/***
	 * Restore reading from a certain row/sheet position and restore schema without
	 * rereadingit.
	 * 
	 * @param split
	 * @param state
	 * @throws IOException
	 */
	public void reopen(FileInputSplit split, Tuple3<Long, Long, GenericDataType[]> state) throws IOException {
		this.customSchema = state.f2;
		this.open(split);
		this.getOfficeReader().getCurrentParser().setCurrentSheet(state.f0);
		this.getOfficeReader().getCurrentParser().setCurrentRow(state.f1);

	}

	/**
	 * Store currently processed sheet and row as well as infered schema
	 * 
	 */
	@Override
	public Tuple3<Long, Long, GenericDataType[]> getCurrentState() throws IOException {
		return new Tuple3<>(this.getOfficeReader().getCurrentParser().getCurrentSheet(),
				this.getOfficeReader().getCurrentParser().getCurrentRow(), this.converter.getSchemaRow());
	}

	@Override
	public TypeInformation<Row> getProducedType() {
		return new RowTypeInfo(this.fieldTypeInfos);
	}

}
