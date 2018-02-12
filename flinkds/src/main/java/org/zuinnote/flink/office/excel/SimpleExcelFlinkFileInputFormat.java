/**
* Copyright 2017 ZuInnoTe (Jörn Franke) <zuinnote@gmail.com>
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.core.fs.FileInputSplit;
import org.zuinnote.flink.office.AbstractSpreadSheetFlinkFileInputFormat;

import org.zuinnote.hadoop.office.format.common.HadoopOfficeReadConfiguration;
import org.zuinnote.hadoop.office.format.common.converter.ExcelConverterSimpleSpreadSheetCellDAO;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericDataType;
import org.zuinnote.hadoop.office.format.common.dao.SpreadSheetCellDAO;

/**
 * @author jornfranke
 *
 */
public class SimpleExcelFlinkFileInputFormat extends AbstractSpreadSheetFlinkFileInputFormat<Object[]>{
	private static final Log LOG = LogFactory.getLog(SimpleExcelFlinkFileInputFormat.class.getName());
	/**
	 * 
	 */
	private static final long serialVersionUID = -1185152489329461987L;
	private long maxInferRows;
	private boolean useHeader;
	private ExcelConverterSimpleSpreadSheetCellDAO converter;
	private HadoopOfficeReadConfiguration shocr;


	/**
	 * 
	 * @param hocr
	 * @param maxInferRows
	 * @param useHeader
	 * @param dateFormat
	 * @param decimalFormat
	 */
	public SimpleExcelFlinkFileInputFormat(HadoopOfficeReadConfiguration hocr, long maxInferRows, boolean useHeader, SimpleDateFormat dateFormat, DecimalFormat decimalFormat) {
		super(hocr, useHeader);
		this.maxInferRows=maxInferRows;
		this.useHeader=useHeader;
		this.converter = new ExcelConverterSimpleSpreadSheetCellDAO(dateFormat,decimalFormat);
		this.shocr=hocr;
		hocr.setMimeType(AbstractSpreadSheetFlinkFileInputFormat.MIMETYPE_EXCEL);
	
	}
	
	/***
	 * get the schema of the underlying data
	 * 
	 * @return
	 */
	
	public GenericDataType[] getSchema() {
		return this.converter.getSchemaRow();
	}

	/**
	 * Open an Excel file
	 * 
	 * @param split contains the Excel file
	 */
	@Override
	public void open(FileInputSplit split) throws IOException {
		// read Excel
		super.open(split);
		// infer schema (requires to read file again)
		ExcelFlinkFileInputFormat effif = new ExcelFlinkFileInputFormat(this.shocr,this.useHeader);
		effif.open(split);
		SpreadSheetCellDAO[] currentRow = effif.nextRecord(null);
		int i=0;
		while ((currentRow!= null) && (i!=this.maxInferRows)) {
			this.converter.updateSpreadSheetCellRowToInferSchemaInformation(currentRow);
			i++;
			currentRow = effif.nextRecord(null);
		}
	}



	@Override
	public Object[] nextRecord(Object[] reuse) throws IOException {
		SpreadSheetCellDAO[] nextRow = (SpreadSheetCellDAO[]) this.readNextRow();
		if (nextRow==null) {
			return null;
		}
		return this.converter.getDataAccordingToSchema(nextRow);

	}

}
