/**
 * Schedule Extractor JS v4 — WakeUp 风格
 * 
 * 核心思路：在 WebView DOM 内直接解析出结构化课程数据，
 * 而不是把 HTML 传回 Kotlin 再用正则解析。
 * 
 * JS 可以直接用 table.rows[i].cells[j] 访问表格结构，
 * 比传 HTML 回去用正则可靠 100 倍。
 * 
 * 返回值：JSON.stringify({
 *   ok: true,
 *   systemType: "QIANGZHI" | "ZHENGFANG" | "URP" | ...,
 *   courses: [{
 *     name: "课程名",
 *     teacher: "教师",
 *     location: "教室",
 *     schedules: [{ day: 1, startPeriod: 1, endPeriod: 2, weeks: [1,2,3...], weekType: "ALL" }]
 *   }]
 * })
 */
(function() {
    'use strict';

    // ============ 工具函数 ============

    function text(el) {
        if (!el) return '';
        // 先用 innerHTML 处理 <br> → 换行，保留换行信息
        var html = el.innerHTML || '';
        return html
            .replace(/<br\s*\/?>/gi, '\n')
            .replace(/<hr[^>]*>/gi, '\n---\n')
            .replace(/<[^>]+>/g, ' ')
            .replace(/&nbsp;/g, ' ')
            .replace(/&#160;/g, ' ')
            .replace(/&amp;/g, '&')
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/[ \t]+/g, ' ')
            .replace(/\n /g, '\n')
            .replace(/ \n/g, '\n')
            .trim();
    }

    function htmlEncode(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // ============ 系统检测 ============

    function detectSystem(doc) {
        var body = text(doc.body);
        var url = (doc.location && doc.location.href) || '';
        
        if (body.indexOf('强智科技') >= 0 || body.indexOf('强智教务') >= 0 || 
            url.indexOf('xskb_list.do') >= 0 || url.indexOf('xskbcx.do') >= 0) return 'QIANGZHI';
        if (body.indexOf('正方软件') >= 0 || body.indexOf('正方教务') >= 0 ||
            url.indexOf('xskbcx.aspx') >= 0 || url.indexOf('default2.aspx') >= 0) return 'ZHENGFANG';
        if (body.indexOf('金智教育') >= 0 || body.indexOf('Wisedu') >= 0 || body.indexOf('wisedu') >= 0) return 'URP';
        if (body.indexOf('树维') >= 0 || body.indexOf('eams') >= 0 || body.indexOf('unitCount') >= 0) return 'SHUWEI';
        if (body.indexOf('青果') >= 0 || body.indexOf('qingoal') >= 0 || body.indexOf('喜鹊儿') >= 0) return 'QINGGUO';
        
        // 按 HTML 结构特征检测
        var html = doc.documentElement.innerHTML || '';
        if (html.indexOf('kbtable') >= 0 || html.indexOf('kbcontent') >= 0) return 'QIANGZHI';
        if (html.indexOf('__VIEWSTATE') >= 0 || html.indexOf('__EVENTVALIDATION') >= 0) return 'ZHENGFANG';
        if (html.indexOf('courseTableForm') >= 0 || html.indexOf('frmright') >= 0) return 'SHUWEI';
        
        return 'GENERIC';
    }

    // ============ 表格识别 ============

    function scoreTable(table) {
        var t = text(table);
        var score = 0;

        if (t.indexOf('星期一') >= 0 || t.indexOf('周一') >= 0) score += 10;
        if (t.indexOf('星期二') >= 0 || t.indexOf('周二') >= 0) score += 5;
        if (/Mon/i.test(t)) score += 8;
        if (t.indexOf('节') >= 0) score += 5;
        if (/\d{2}:\d{2}/.test(t)) score += 3;
        if (table.id === 'kbtable' || t.indexOf('kbtable') >= 0) score += 20;
        if (table.id === 'Table1' || table.id === 'table1') score += 15;

        var rows = table.rows;
        if (rows && rows.length > 3) score += 3;
        if (rows && rows.length > 5) score += 2;

        var tdCount = table.querySelectorAll('td').length;
        if (tdCount >= 7) score += 3;
        if (tdCount >= 20) score += 5;
        if (tdCount < 7) score -= 10;

        if (/[一二三四五六日]/.test(t)) score += 8;
        if (/\d+[-~]\d+.*周/.test(t)) score += 5;
        if (/\d+.*楼/.test(t) || /\d+.*室/.test(t)) score += 3;

        return score;
    }

    function findBestTable(doc) {
        var tables = doc.querySelectorAll('table');
        var bestTable = null;
        var bestScore = 0;

        for (var i = 0; i < tables.length; i++) {
            var score = scoreTable(tables[i]);
            if (score > bestScore) {
                bestScore = score;
                bestTable = tables[i];
            }
        }

        return { table: bestTable, score: bestScore };
    }

    // ============ 周次解析 ============

    /**
     * 解析周次字符串，返回周次数组
     * 支持：1-16周, 1-8(周), 1-16(单), 1-16(双), 1,3,5周, 单周, 双周
     */
    function parseWeeks(str) {
        if (!str) return [];
        str = str.replace(/\s/g, '');
        
        var weeks = [];
        var isOdd = false, isEven = false;
        
        if (/单/.test(str)) isOdd = true;
        if (/双/.test(str)) isEven = true;
        
        // 提取数字范围：1-16, 1~16
        var rangePattern = /(\d+)[-~](\d+)/g;
        var match;
        var found = false;
        
        while ((match = rangePattern.exec(str)) !== null) {
            found = true;
            var start = parseInt(match[1]);
            var end = parseInt(match[2]);
            for (var i = start; i <= end; i++) {
                if (isOdd && i % 2 === 0) continue;
                if (isEven && i % 2 === 1) continue;
                weeks.push(i);
            }
        }
        
        if (!found) {
            // 离散周次：1,3,5,7
            var numPattern = /\d+/g;
            while ((match = numPattern.exec(str)) !== null) {
                var num = parseInt(match[0]);
                if (num > 0 && num <= 30) {
                    if (isOdd && num % 2 === 0) continue;
                    if (isEven && num % 2 === 1) continue;
                    weeks.push(num);
                }
            }
        }
        
        // 去重排序
        weeks = weeks.filter(function(v, i, a) { return a.indexOf(v) === i; });
        weeks.sort(function(a, b) { return a - b; });
        
        return weeks;
    }

    /**
     * 解析周次类型
     */
    function parseWeekType(str) {
        if (!str) return 'ALL';
        if (/单/.test(str)) return 'ODD';
        if (/双/.test(str)) return 'EVEN';
        return 'ALL';
    }

    // ============ 节次解析 ============

    /**
     * 从文本中提取节次范围
     * 支持：第1-2节, 1-2节, 1,2节, 第1大节, 08:00-09:40
     */
    function parsePeriods(str) {
        if (!str) return null;
        
        // 范围：1-2节, 第1-2节, 1~2节, (11-12节)
        var rangeMatch = str.match(/(\d+)[-~](\d+)\s*节/);
        if (rangeMatch) {
            return { start: parseInt(rangeMatch[1]), end: parseInt(rangeMatch[2]) };
        }
        
        // 单节：第1节
        var singleMatch = str.match(/第?(\d+)\s*节/);
        if (singleMatch) {
            var p = parseInt(singleMatch[1]);
            return { start: p, end: p };
        }
        
        // 离散：1,2,3节, 03,04节, (03,04节)
        var discreteMatch = str.match(/(\d+(?:,\d+)*)\s*节/);
        if (discreteMatch) {
            var nums = discreteMatch[1].split(',').map(function(s) { return parseInt(s); });
            if (nums.length > 0) {
                return { start: Math.min.apply(null, nums), end: Math.max.apply(null, nums) };
            }
        }
        
        return null;
    }

    // ============ 星期解析 ============

    function parseDay(str) {
        if (!str) return 0;
        if (/一|Mon/i.test(str)) return 1;
        if (/二|Tue/i.test(str)) return 2;
        if (/三|Wed/i.test(str)) return 3;
        if (/四|Thu/i.test(str)) return 4;
        if (/五|Fri/i.test(str)) return 5;
        if (/六|Sat/i.test(str)) return 6;
        if (/日|天|Sun/i.test(str)) return 7;
        return 0;
    }

    // ============ 节次标签解析 ============

    /**
     * 从行级节次标签中提取起始节次
     * 支持：
     * - "第二大节（03,04,05小节）" → 03
     * - "第一二大节（01,02小节）" → 01
     * - "第1-2节" → 1
     * - "1-2节" → 1
     */
    function parsePeriodLabel(str) {
        if (!str) return 0;
        
        // 优先：从括号中提取小节数字 (NN,MM小节)
        var parenMatch = str.match(/\((\d+)(?:[,-](\d+))*.*小节/);
        if (parenMatch) {
            return parseInt(parenMatch[1]);
        }
        
        // 范围：第1-2节, 1-2节
        var rangeMatch = str.match(/第?(\d+)[-~](\d+)\s*节/);
        if (rangeMatch) {
            return parseInt(rangeMatch[1]);
        }
        
        // 单节：第1节
        var singleMatch = str.match(/第?(\d+)\s*节/);
        if (singleMatch) {
            return parseInt(singleMatch[1]);
        }
        
        // 阿拉伯数字开头：1、2、3...
        var numMatch = str.match(/^(\d+)/);
        if (numMatch && /节|大节/.test(str)) {
            return parseInt(numMatch[1]);
        }
        
        return 0;
    }

    // ============ 课程单元格解析 ============

    /**
     * 解析单元格中的课程信息
     * 支持多种格式：
     * 1. 多行格式（<br> 分隔）
     * 2. @ 分隔格式
     * 3. 单行格式
     */
    function parseCourseCell(cellText, day, defaultStartPeriod, defaultEndPeriod) {
        if (!cellText || cellText.length < 2) return null;
        
        // 跳过表头和空内容
        if (/^星期|^节次|^Mon|^Tue|^Wed|^Thu|^Fri|^Sat|^Sun/i.test(cellText.trim())) return null;
        
        var lines = cellText.split(/[\n\r]+/).map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
        if (lines.length === 0) return null;
        
        var name = '', teacher = '', location = '', weekStr = '', periodStr = '';
        
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            
            // 跳过分隔线
            if (/^[-=]{3,}$/.test(line)) continue;
            
            // 周次信息 — 支持 [05-16周] 格式
            // 但要小心："[05-16周] 神经网络与深度学习" 是课程名，不是纯周次
            var weekMatch = line.match(/^[\[（(]?\d+[-~,，]?\d*周[\]）)]?\s*(.*)/);
            var afterWeek = weekMatch ? (weekMatch[1] || '').trim() : '';
            if (weekMatch && afterWeek.length < 2) {
                // 纯周次行："[05-16周]" 或 "1-16周"
                if (!weekStr) weekStr = line;
                continue;
            } else if (weekMatch && afterWeek.length >= 2) {
                // 有后续内容
                var weekPrefix = line.match(/^[\[（(]?\d+[-~,，]?\d*周[\]）)]?/)?.[0] || '';
                if (!weekStr) weekStr = weekPrefix;
                // 检查后续是否是节次信息：(06,07节)
                if (/\d+.*节/.test(afterWeek)) {
                    if (!periodStr) periodStr = afterWeek;
                    continue;
                }
                // 检查后续是否是地点信息
                var afterLoc = afterWeek;
                if (/[楼室厅教栋馆场山园]\D/.test(afterLoc) || /\d+.*楼/.test(afterLoc)) {
                    if (!location) location = afterLoc;
                    continue;
                }
                // 否则是课程名
                var nameCandidate = afterWeek;
                if (!name && nameCandidate.length >= 2 && nameCandidate.length <= 30 && !/^\d+$/.test(nameCandidate)) {
                    name = nameCandidate;
                }
                continue;
            }
            
            // 节次信息 — 支持 (03,04节) 格式
            if (/\d+.*节/.test(line)) {
                if (!periodStr) periodStr = line;
                continue;
            }
            
            // 教室/地点 — 支持"美达楼讲空二"、"美达山-人工草坪足场"等
            // 也要处理带周次前缀的情况："[05-16周] 美达楼讲空二"
            var locCandidate = line.replace(/^\[\d+[-~]?\d*周\]\s*/, '');
            if (/[楼室厅教栋馆场山园]\D/.test(locCandidate) || /\d+.*楼/.test(locCandidate) || /\d+.*室/.test(locCandidate)) {
                if (!location) location = locCandidate;
                continue;
            }
            
            // 教师名（2-4个汉字，或包含"老师"）
            if (/^[\u4e00-\u9fa5]{2,4}$/.test(line) || /老师/.test(line)) {
                if (!teacher) teacher = line.replace('老师', '');
                continue;
            }
            
            // 课程名 — 第一个不匹配其他模式的行
            // 支持 [05-16周] 课程名 格式（去掉方括号前缀）
            var nameCandidate = line.replace(/^\[\d+[-~]?\d*周\]\s*/, '');
            if (!name && nameCandidate.length >= 2 && nameCandidate.length <= 30 && !/^\d+$/.test(nameCandidate)) {
                name = nameCandidate;
            }
        }
        
        if (!name) return null;
        
        // 解析周次
        var weeks = parseWeeks(weekStr);
        var weekType = parseWeekType(weekStr);
        // 强智表格：优先用行级节次（defaultStartPeriod/endPeriod）
        // 因为单元格内的 "22(03,04节)" 中的数字可能包含班级号
        var startPeriod = defaultStartPeriod;
        var endPeriod = defaultEndPeriod;
        // 只有当 periodStr 明确是节次格式时才用它
        if (periodStr) {
            var parsedPeriod = parsePeriods(periodStr);
            if (parsedPeriod && parsedPeriod.start >= 1 && parsedPeriod.start <= 20) {
                startPeriod = parsedPeriod.start;
                endPeriod = parsedPeriod.end;
            }
        }
        
        return {
            name: name,
            teacher: teacher,
            location: location,
            schedules: [{
                day: day,
                startPeriod: startPeriod,
                endPeriod: endPeriod,
                weeks: weeks,
                weekType: weekType
            }]
        };
    }

    // ============ 正方教务专用解析 ============

    /**
     * 正方教务系统表格解析
     * 特点：Table1/table1，单元格用 <br> 分隔，多个课程用 ----- 分隔
     */
    function parseZhengfangTable(table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;
        
        // 找表头行（包含星期）
        var headerRow = -1;
        var dayCols = {};
        
        for (var r = 0; r < rows.length; r++) {
            var rowText = text(rows[r]);
            if (rowText.indexOf('星期一') >= 0 || rowText.indexOf('周一') >= 0 || /Mon/i.test(rowText)) {
                headerRow = r;
                // 解析列对应的星期
                var cells = rows[r].cells;
                var colIdx = 0;
                for (var c = 0; c < cells.length; c++) {
                    var cellText = text(cells[c]);
                    var day = parseDay(cellText);
                    if (day > 0) {
                        dayCols[colIdx] = day;
                    }
                    colIdx += (cells[c].colSpan || 1);
                }
                break;
            }
        }
        
        if (headerRow < 0) return courses;
        
        // 遍历数据行
        var currentPeriod = 0;
        
        for (var r = headerRow + 1; r < rows.length; r++) {
            var row = rows[r];
            var cells = row.cells;
            if (!cells) continue;
            
            // 检测节次标签 — 支持中文数字和 (NN,MM小节) 格式
            var firstCellText = text(cells[0]);
            var currentRowPeriod = parsePeriodLabel(firstCellText);
            if (currentRowPeriod > 0) {
                currentPeriod = currentRowPeriod;
            }
            
            var colIdx = 0;
            for (var c = 0; c < cells.length; c++) {
                var cell = cells[c];
                var colspan = cell.colSpan || 1;
                var rowspan = cell.rowSpan || 1;
                
                var day = dayCols[colIdx] || 0;
                colIdx += colspan;
                
                if (day === 0) continue;
                
                var cellText = text(cell);
                if (cellText.length < 3) continue;
                
                // 正方特有：用 ----- 分隔多个课程
                var parts = cell.innerHTML.split(/<hr[^>]*>|---+|===+/i);
                
                for (var p = 0; p < parts.length; p++) {
                    var partText = parts[p].replace(/<br\s*\/?>/gi, '\n')
                        .replace(/<[^>]+>/g, ' ')
                        .replace(/&nbsp;/g, ' ')
                        .replace(/&#160;/g, ' ')
                        .replace(/&amp;/g, '&')
                        .replace(/\s+/g, ' ')
                        .trim();
                    
                    var course = parseCourseCell(partText, day, currentPeriod, currentPeriod + rowspan - 1);
                    if (course) {
                        courses.push(course);
                    }
                }
            }
        }
        
        return courses;
    }

    // ============ 强智教务专用解析 ============

    /**
     * 强智教务系统 HTML 表格解析
     * 特点：kbtable 网格，单元格用 <br> 分隔
     */
    function parseQiangzhiTable(table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;
        
        // 找表头行
        var headerRow = -1;
        var dayCols = {};
        
        for (var r = 0; r < rows.length; r++) {
            var rowText = text(rows[r]);
            if (rowText.indexOf('星期一') >= 0 || rowText.indexOf('周一') >= 0 || /Mon/i.test(rowText)) {
                headerRow = r;
                var cells = rows[r].cells;
                var colIdx = 0;
                for (var c = 0; c < cells.length; c++) {
                    var cellText = text(cells[c]);
                    var day = parseDay(cellText);
                    if (day > 0) {
                        dayCols[colIdx] = day;
                    }
                    colIdx += (cells[c].colSpan || 1);
                }
                break;
            }
        }
        
        if (headerRow < 0) return courses;
        
        // 遍历数据行
        var currentPeriod = 0;
        
        for (var r = headerRow + 1; r < rows.length; r++) {
            var row = rows[r];
            var cells = row.cells;
            if (!cells) continue;
            
            // 检测节次标签 — 支持中文数字和 (NN,MM小节) 格式
            var firstCellText = text(cells[0]);
            var currentRowPeriod = parsePeriodLabel(firstCellText);
            if (currentRowPeriod > 0) {
                currentPeriod = currentRowPeriod;
            }
            
            var colIdx = 0;
            for (var c = 0; c < cells.length; c++) {
                var cell = cells[c];
                var colspan = cell.colSpan || 1;
                var rowspan = cell.rowSpan || 1;
                
                var day = dayCols[colIdx] || 0;
                colIdx += colspan;
                
                if (day === 0) continue;
                
                var cellText = text(cell);
                if (cellText.length < 3) continue;
                
                var course = parseCourseCell(cellText, day, currentPeriod, currentPeriod + rowspan - 1);
                if (course) {
                    courses.push(course);
                }
            }
        }
        
        return courses;
    }

    // ============ 通用网格解析 ============

    /**
     * 通用表格解析器（用于 URP、树维、青果、未知系统）
     */
    function parseGenericTable(table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;
        
        // 找表头行
        var headerRow = -1;
        var dayCols = {};
        
        for (var r = 0; r < rows.length; r++) {
            var rowText = text(rows[r]);
            if (rowText.indexOf('星期一') >= 0 || rowText.indexOf('周一') >= 0 || /Mon/i.test(rowText)) {
                headerRow = r;
                var cells = rows[r].cells;
                var colIdx = 0;
                for (var c = 0; c < cells.length; c++) {
                    var cellText = text(cells[c]);
                    var day = parseDay(cellText);
                    if (day > 0) {
                        dayCols[colIdx] = day;
                    }
                    colIdx += (cells[c].colSpan || 1);
                }
                break;
            }
        }
        
        if (headerRow < 0) return courses;
        
        // 遍历数据行
        var currentPeriod = 0;
        
        for (var r = headerRow + 1; r < rows.length; r++) {
            var row = rows[r];
            var cells = row.cells;
            if (!cells) continue;
            
            // 检测节次标签 — 支持中文数字和 (NN,MM小节) 格式
            var firstCellText = text(cells[0]);
            var currentRowPeriod = parsePeriodLabel(firstCellText);
            if (currentRowPeriod > 0) {
                currentPeriod = currentRowPeriod;
            }
            
            var colIdx = 0;
            for (var c = 0; c < cells.length; c++) {
                var cell = cells[c];
                var colspan = cell.colSpan || 1;
                var rowspan = cell.rowSpan || 1;
                
                var day = dayCols[colIdx] || 0;
                colIdx += colspan;
                
                if (day === 0) continue;
                
                var cellText = text(cell);
                if (cellText.length < 3) continue;
                
                var course = parseCourseCell(cellText, day, currentPeriod, currentPeriod + rowspan - 1);
                if (course) {
                    courses.push(course);
                }
            }
        }
        
        return courses;
    }

    // ============ 课程合并 ============

    function mergeCourses(courses) {
        var map = {};
        var result = [];
        
        for (var i = 0; i < courses.length; i++) {
            var c = courses[i];
            var key = c.name + '_' + c.teacher;
            
            if (map[key]) {
                // 合并 schedules
                for (var j = 0; j < c.schedules.length; j++) {
                    map[key].schedules.push(c.schedules[j]);
                }
            } else {
                map[key] = {
                    name: c.name,
                    teacher: c.teacher,
                    location: c.location,
                    schedules: c.schedules.slice()
                };
                result.push(map[key]);
            }
        }
        
        return result;
    }

    // ============ 主入口 ============

    function extract() {
        try {
            // 1. 检测系统类型
            var systemType = detectSystem(document);
            
            // 2. 查找课表表格
            var result = findBestTable(document);
            var bestTable = result.table;
            var bestScore = result.score;
            
            // 3. 如果没找到，尝试 iframe
            if (!bestTable || bestScore < 5) {
                var frames = document.querySelectorAll('iframe');
                for (var f = 0; f < frames.length; f++) {
                    try {
                        var fDoc = frames[f].contentDocument || frames[f].contentWindow.document;
                        if (!fDoc) continue;
                        var fResult = findBestTable(fDoc);
                        if (fResult.score > bestScore) {
                            bestScore = fResult.score;
                            bestTable = fResult.table;
                        }
                    } catch(e) { /* cross-origin iframe, skip */ }
                }
            }
            
            if (!bestTable || bestScore < 5) {
                return { ok: false, reason: '未找到课程表表格（score=' + bestScore + '）' };
            }
            
            // 4. 根据系统类型选择解析器
            var courses;
            switch (systemType) {
                case 'ZHENGFANG':
                    courses = parseZhengfangTable(bestTable);
                    break;
                case 'QIANGZHI':
                    courses = parseQiangzhiTable(bestTable);
                    break;
                default:
                    courses = parseGenericTable(bestTable);
                    break;
            }
            
            // 5. 合并同名课程
            courses = mergeCourses(courses);
            
            if (courses.length === 0) {
                return { ok: false, reason: '表格已找到（score=' + bestScore + '）但未解析到课程数据' };
            }
            
            return {
                ok: true,
                systemType: systemType,
                courses: courses,
                tableScore: bestScore
            };
            
        } catch(e) {
            return { ok: false, reason: '提取异常: ' + e.message + '\n' + e.stack };
        }
    }

    return JSON.stringify(extract());
})();
