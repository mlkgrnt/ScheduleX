package com.schedulex.llm

/**
 * Extracts schedule HTML from any frame (including iframes).
 * Returns a JS expression that works recursively through all frames.
 */
object HtmlExtractor {

    // This JS searches the current document AND all iframes for schedule content
    const val EXTRACT_JS = """
(function() {
    try {
    var diag = [];
    diag.push('doc_title=' + document.title);
    diag.push('doc_url=' + document.location.href);
    diag.push('body_len=' + (document.body ? document.body.innerHTML.length : 0));
    
    var iframes = document.querySelectorAll('iframe');
    diag.push('iframe_count=' + iframes.length);
    for (var i = 0; i < iframes.length; i++) {
        try {
            var src = iframes[i].src || '(no src)';
            diag.push('iframe_' + i + '_src=' + src);
            var doc = iframes[i].contentDocument || iframes[i].contentWindow.document;
            diag.push('iframe_' + i + '_body_len=' + (doc ? doc.body.innerHTML.length : 0));
            var tables = doc ? doc.querySelectorAll('table') : [];
            diag.push('iframe_' + i + '_tables=' + tables.length);
            if (tables.length > 0) {
                for (var j = 0; j < Math.min(tables.length, 3); j++) {
                    diag.push('iframe_' + i + '_table_' + j + '_text=' + (tables[j].innerText || '').substring(0, 200));
                }
            }
        } catch(e) {
            diag.push('iframe_' + i + '_error=' + e.message);
        }
    }
    
    var mainTables = document.querySelectorAll('table');
    diag.push('main_tables=' + mainTables.length);
    } catch(diagErr) {
        // diag failed, continue anyway
    }
    
    function cleanHtml(html) {
        var cleaned = html
            .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
            .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
            .replace(/<nav[^>]*>[\s\S]*?<\/nav>/gi, '')
            .replace(/<footer[^>]*>[\s\S]*?<\/footer>/gi, '')
            .replace(/<!--[\s\S]*?-->/gi, '')
            .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '');
        if (cleaned.length > 30000) cleaned = cleaned.substring(0, 30000);
        return cleaned;
    }

    function findSchedule(doc) {
        if (!doc) return null;
        
        // Try known selectors
        var selectors = [
            '#kbtable', '#kbcontent', '#courseTable', '#mytable',
            '.kbtable', '.kbcontent', '.courseTable',
            '#mainTable', '#table1', '#DataGrid1',
            '#kbdiv', '.kbdiv', '#xskbtable'
        ];
        
        for (var i = 0; i < selectors.length; i++) {
            try {
                var el = doc.querySelector(selectors[i]);
                if (el && el.innerHTML.trim().length > 50) {
                    return cleanHtml(el.outerHTML);
                }
            } catch(e) {}
        }
        
        // Find tables with course content
        try {
            var tables = doc.querySelectorAll('table');
            var bestTable = null;
            var bestScore = 0;
            
            for (var i = 0; i < tables.length; i++) {
                var t = tables[i];
                var text = t.innerText || '';
                var trCount = t.querySelectorAll('tr').length;
                var tdCount = t.querySelectorAll('td').length;
                var courseKeywords = (text.match(/[周节课程教室老师/g) || []).length;
                var score = trCount * tdCount + courseKeywords * 5;
                if (score > bestScore) {
                    bestScore = score;
                    bestTable = t;
                }
            }
            
            if (bestTable && bestScore > 10) {
                return cleanHtml(bestTable.outerHTML);
            }
        } catch(e) {}
        
        // Try divs with course content
        try {
            var divs = doc.querySelectorAll('div');
            for (var i = 0; i < divs.length; i++) {
                var d = divs[i];
                var text = d.innerText || '';
                if (text.indexOf('周') !== -1 && text.indexOf('节') !== -1 && text.length > 200) {
                    var tables = d.querySelectorAll('table');
                    if (tables.length > 0) {
                        return cleanHtml(d.outerHTML);
                    }
                }
            }
        } catch(e) {}
        
        return null;
    }

    // Search main document first
    var result = findSchedule(document);
    if (result) return result;

    // Search all iframes (强智系统常用 iframe)
    var iframes = document.querySelectorAll('iframe');
    for (var i = 0; i < iframes.length; i++) {
        try {
            var iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
            result = findSchedule(iframeDoc);
            if (result) return result;
        } catch(e) {
            // Cross-origin iframe, can't access
        }
    }

    // Last resort: return body with diagnostics
    var bodyLen = document.body ? document.body.innerHTML.length : 0;
    diag.push('fallback_body_len=' + bodyLen);
    if (bodyLen > 100) {
        // Return first 500 chars of body for debugging
        diag.push('body_preview=' + (document.body.innerHTML || '').substring(0, 500));
    }
    return 'DIAG:' + diag.join('|') + '\n' + cleanHtml(document.body ? document.body.innerHTML : '');
})()
"""
}
