<#if element.properties.isPreview! == 'true' >
    <script>
        $(document).ready(function() {
            $(".form-button").attr("disabled", "disabled");
        });
    </script>
</#if>

<div class="viewExcelCsvDataFieldExtraction-body-content">
    <style>
        .excelcsv-filepicker { display: inline-flex; align-items: center; gap: 0.5em; flex-wrap: wrap; }
        .excelcsv-filepicker .excelcsv-fileinput { position: absolute; left: -99999px; width: 1px; height: 1px; overflow: hidden; }
        .excelcsv-filepicker .excelcsv-choosebtn {
            display: inline-block;
            padding: 0.35em 0.75em;
            border: 1px solid #c7c7c7;
            border-radius: 4px;
            background: #f8f8f8;
            color: #222;
            cursor: pointer;
            user-select: none;
            line-height: 1.2;
        }
        .excelcsv-filepicker .excelcsv-choosebtn:hover { background: #f2f2f2; }
        .excelcsv-filepicker .excelcsv-filename { color: #555; }
    </style>
    <#if element.properties.customHeader! == '' >
        <h3>${element.properties.label!}</h3>
    <#else>
        ${element.properties.customHeader!}
    </#if>

    <#if element.properties.error! == 'true' >
        <style>
            .errors{color:red; margin-bottom:20px;}
        </style>
        <div class="errors">
            <span>
                <#if element.properties.messageOnError! != '' >
                    ${element.properties.messageOnError!}
                <#else>
                    @@userview.excelcsvdatafieldextraction.error@@
                </#if>
            </span>
        </div>
    </#if>

    <#if element.properties.hasExtractedData! == 'true' && element.properties.messageOnSuccess! != '' >
        <script>
            $(document).ready(function() { alert("${element.properties.messageOnSuccess!}"); });
        </script>
    </#if>
    <#if element.properties.hasExtractedData! == 'true' && element.properties.redirectUrl! != '' >
        <script>
            $(document).ready(function() { parent.location = "${element.properties.redirectUrl!}"; });
        </script>
    </#if>

    <#if element.properties.view! == 'displayForm'>
        <fieldset id="form-canvas">
            <form id="excelCsvDataFieldExtractionForm" method="post" action="${element.properties.url!}" class="form form-container" enctype="multipart/form-data">
                <input type="hidden" id="doAction" name="doAction" value=""/>
                <input type="hidden" id="previewPage" name="previewPage" value="${previewPage!1}"/>
                <div class="form-section no_label" id="section_upload">
                    <div class="form-section-title"></div>
                    <div style="width: 100%" class="form-column" id="">
                        <div class="form-cell">
                            <label for="csvImportFile" class="label upload">@@general.method.label.selectFile@@ <span class="form-cell-validator">*</span></label>
                            <div class="excelcsv-filepicker">
                                <input id="csvImportFile" class="excelcsv-fileinput" type="file" name="csvImportFile"/>
                                <label for="csvImportFile" class="excelcsv-choosebtn">Choose file</label>
                                <span id="selectedFileName" class="excelcsv-filename">
                                    <#if uploadedFilename?? && uploadedFilename?has_content>
                                        ${uploadedFilename?html}
                                    <#else>
                                        No file chosen
                                    </#if>
                                </span>
                            </div>
                        </div>
                        <#if isExcelFile?? && isExcelFile == 'true' && sheetNames??>
                            <div class="form-cell">
                                <label for="sheetIndex" class="label upload">Worksheet</label>
                                <div class="form-cell-value">
                                    <select id="sheetIndex" name="sheetIndex" class="form-control">
                                        <#list sheetNames as sn>
                                            <option value="${sn_index}" <#if selectedSheetIndex?? && selectedSheetIndex?number == sn_index>selected</#if>>${sn?html}</option>
                                        </#list>
                                    </select>
                                </div>
                            </div>
                        </#if>
                        <div class="form-cell">
                            <label for="mode" class="label upload">@@userview.excelcsvdatafieldextraction.importMode@@</label>
                            <div class="form-cell-value">
                                <label style="display:block; width:100%; float:none;">
                                    <input class="form-check-input" type="radio" value="NEW" name="mode"><i></i>
                                    @@userview.excelcsvdatafieldextraction.importMode.new@@
                                </label>
                                <label style="display:block; width:100%; float:none;">
                                    <input class="form-check-input" type="radio" value="NEW & UPDATE" name="mode" checked><i></i>
                                    @@userview.excelcsvdatafieldextraction.importMode.new_update@@
                                </label>
                                <#if element.properties.disabledDelete! != 'true' >
                                    <label style="display:block; width:100%; float:none;">
                                        <input class="form-check-input" type="radio" value="DELETE" name="mode"><i></i>
                                        @@userview.excelcsvdatafieldextraction.importMode.delete@@
                                    </label>
                                </#if>
                            </div>
                        </div>
                        <div class="form-cell">
                            <label for="validateData" class="label upload">@@userview.excelcsvdatafieldextraction.validateData@@</label>
                            <div class="form-cell-value">
                                <label>
                                    <input class="form-check-input" type="checkbox" value="true" name="validateData" checked><i></i>
                                </label>
                            </div>
                        </div>
                    </div>
                    <div style="clear:both"></div>
                </div>
                <div class="form-section no_label section_" id="section-actions">
                    <div class="form-section-title"></div>
                    <div style="width: " class="form-column form-column-horizontal" id="">
                        <div class="form-cell">
                            <button type="button" class="form-button btn button" id="previewBtn">@@userview.excelcsvdatafieldextraction.extract@@</button>
                            <button type="button" class="form-button btn button" id="importBtn">@@userview.excelcsvdatafieldextraction.import@@</button>
                        </div>
                    </div>
                </div>
            </form>
        </fieldset>
    </#if>

    <#if extractedHeaders?? && extractedRows?? >
        <div class="extracted-data" style="margin-top:1.5em;">
            <h4 style="margin-bottom:0.5em;">@@userview.excelcsvdatafieldextraction.extractedTitle@@</h4>
            <p style="margin:0 0 0.75em 0;color:#666;">
                ${extractedRowCount!0} @@userview.excelcsvdatafieldextraction.rows@@, ${extractedColumnCount!0} @@userview.excelcsvdatafieldextraction.columns@@
            </p>
            <div style="overflow-x:auto;">
                <table cellspacing="0" class="table table-bordered table-striped" style="width:100%;">
                    <thead>
                    <tr>
                        <#list extractedHeaders as h>
                            <th>${h?html}</th>
                        </#list>
                    </tr>
                    </thead>
                    <tbody>
                    <#list extractedRows as r>
                        <tr class="grid-row">
                            <#list r as c>
                                <td>${c?html}</td>
                            </#list>
                        </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
            <#if previewTotalPages?? && (previewTotalPages > 1) >
                <#assign _pp = previewPage!1 />
                <#assign _prev = _pp - 1 />
                <#assign _next = _pp + 1 />
                <#assign _base = element.properties.url! />
                <div style="display:flex;flex-wrap:wrap;align-items:center;gap:0.75em;margin:0.75em 0 0 0;color:#444;">
                    <span>@@userview.excelcsvdatafieldextraction.previewPage@@ ${_pp?c} / ${previewTotalPages?c}</span>
                    <span style="color:#666;">@@userview.excelcsvdatafieldextraction.previewRows@@ ${previewFromRow!0}–${previewToRow!0}</span>
                    <span style="margin-left:auto;display:inline-flex;gap:0.5em;">
                        <#if _pp gt 1 >
                            <form method="post" action="${_base}" style="display:inline;margin:0;padding:0;">
                                <input type="hidden" name="doAction" value="preview"/>
                                <input type="hidden" name="previewPage" value="${_prev?c}"/>
                                <#if isExcelFile?? && isExcelFile == 'true' && (selectedSheetIndex??) >
                                    <input type="hidden" name="sheetIndex" value="${selectedSheetIndex?c}"/>
                                </#if>
                                <button type="submit" class="form-button btn button" style="padding:0.35em 0.75em;">@@userview.excelcsvdatafieldextraction.previewPrev@@</button>
                            </form>
                        <#else>
                            <span class="form-button btn button" style="opacity:0.45;cursor:not-allowed;padding:0.35em 0.75em;">@@userview.excelcsvdatafieldextraction.previewPrev@@</span>
                        </#if>
                        <#if _pp lt previewTotalPages >
                            <form method="post" action="${_base}" style="display:inline;margin:0;padding:0;">
                                <input type="hidden" name="doAction" value="preview"/>
                                <input type="hidden" name="previewPage" value="${_next?c}"/>
                                <#if isExcelFile?? && isExcelFile == 'true' && (selectedSheetIndex??) >
                                    <input type="hidden" name="sheetIndex" value="${selectedSheetIndex?c}"/>
                                </#if>
                                <button type="submit" class="form-button btn button" style="padding:0.35em 0.75em;">@@userview.excelcsvdatafieldextraction.previewNext@@</button>
                            </form>
                        <#else>
                            <span class="form-button btn button" style="opacity:0.45;cursor:not-allowed;padding:0.35em 0.75em;">@@userview.excelcsvdatafieldextraction.previewNext@@</span>
                        </#if>
                    </span>
                </div>
            </#if>
        </div>
    </#if>

    <#if element.properties.view! == 'success'>
        <div class="result" style="margin-top:1.5em;">
            <style>
                .toggle{display:block; margin-top:5px;cursor:pointer;}
            </style>
            <#if element.properties.mode! == 'DELETE' >
                <a class="toggle">${successDeletedCount!} @@userview.excelcsvdatafieldextraction.recordsDeleted@@</a>
                <div style="display:none">
                    @@userview.excelcsvdatafieldextraction.rowNum@@<br/>
                    ${successDeletedRows!}
                </div>
            <#else>
                <a class="toggle">${successImportedCount!} @@userview.excelcsvdatafieldextraction.recordsImported@@</a>
                <div style="display:none">
                    @@userview.excelcsvdatafieldextraction.rowNum@@<br/>
                    ${successImportedRows!}
                </div>
                <#if successUpdatedCount! != 0 >
                    <a class="toggle">${successUpdatedCount!} @@userview.excelcsvdatafieldextraction.recordsUpdated@@</a>
                    <div style="display:none">
                        @@userview.excelcsvdatafieldextraction.rowNum@@<br/>
                        ${successUpdatedRows!}
                    </div>
                </#if>
            </#if>
            <#if skippedCount! != 0 >
                <a class="toggle">${skippedCount!} @@userview.excelcsvdatafieldextraction.recordsSkipped@@</a>
                <div style="display:none">
                    @@userview.excelcsvdatafieldextraction.rowNum@@<br/>
                    ${skippedRows!}
                </div>
            </#if>
            <#if validationErrorCount! != 0 >
                <a class="toggle">${validationErrorCount!} @@userview.excelcsvdatafieldextraction.recordsError@@</a>
                <div style="display:none">
                    <table cellspacing="0" style="width:100%;" class="table table-bordered">
                        <tbody>
                        <tr>
                            <th width="20%">@@userview.excelcsvdatafieldextraction.row@@</th>
                            <th>@@userview.excelcsvdatafieldextraction.errors@@</th>
                        </tr>
                        <#list validationErrorRows?keys as row>
                            <tr class="grid-row">
                                <td>${row}</td>
                                <td>${validationErrorRows[row]}</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>
            </#if>
        </div>
    </#if>

    <#if element.properties.customFooter! != '' >
        ${element.properties.customFooter!}
    </#if>
</div>
<script>
    $(document).ready(function(){
        <#if element.properties.corfirmation! != '' >
            $("form#excelCsvDataFieldExtractionForm").submit(function(){
                return confirm("${element.properties.corfirmation!}");
            });
        </#if>
        $(".toggle").click(function(){
            $(this).next().toggle();
        });

        // Note: do not auto-submit on file selection.
        $("#csvImportFile").on("change", function() {
            var name = (this.files && this.files.length && this.files[0] && this.files[0].name) ? this.files[0].name : "No file chosen";
            $("#selectedFileName").text(name);
        });

        // If user changes worksheet, re-run preview using cached upload (no re-select needed)
        $("#sheetIndex").on("change", function() {
            $("#doAction").val("preview");
            $("#previewPage").val("1");
            $("#excelCsvDataFieldExtractionForm").submit();
        });

        $("#previewBtn").on("click", function() {
            $("#doAction").val("preview");
            $("#previewPage").val("1");
            $("#excelCsvDataFieldExtractionForm").submit();
        });

        $("#importBtn").on("click", function() {
            $("#doAction").val("import");
            $("#excelCsvDataFieldExtractionForm").submit();
        });
    });
</script>
