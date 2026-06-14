package com.schedulex.llm

/**
 * 课表HTML预处理器
 * 先检测教务系统类型，再匹配对应解析器链
 * 都失败才fallback到LLM（此时会智能过滤HTML）
 */
object ScheduleHtmlCleaner {

    const val CLEAN_EXTRACT_JS = """
(function() {
    try {
        var url = window.location.href.toLowerCase();
        var body = document.body.innerHTML;
        var systemType = detectSystem(url, body);

        var courses = [];
        var parseError = '';

        switch (systemType) {
            case 'qiangzhi': courses = parseQiangzhi(); break;
            case 'zhengfang': courses = parseZhengfang(); break;
            case 'urp': courses = parseURP(); break;
            case 'jinzhi': courses = parseJinZhi(); break;
            default: parseError = 'unknown_system'; break;
        }

        if (courses.length === 0 && !parseError) {
            parseError = 'no_courses:' + systemType;
        }

        // 如果解析失败，返回智能过滤后的HTML供LLM兜底
        var filteredHtml = null;
        if (courses.length === 0) {
            filteredHtml = getFilteredHtml(systemType);
        }

        return JSON.stringify({
            system: systemType,
            courses: courses,
            filteredHtml: filteredHtml,
            error: parseError || null
        });

    } catch(e) {
        return JSON.stringify({error: e.message});
    }

    // ================================================================
    //  系统检测
    // ================================================================
    function detectSystem(url, body) {
        if (url.indexOf('/jsxsd/') >= 0) return 'qiangzhi';
        if (document.getElementById('kbtable') || document.getElementById('timetable')) return 'qiangzhi';
        if (body.indexOf('class="kbcontent"') >= 0) return 'qiangzhi';
        if (url.indexOf('/jwglxt/') >= 0 || url.indexOf('/jwgl/') >= 0) return 'zhengfang';
        if (document.getElementById('table1') || document.getElementById('Table1') || document.getElementById('kbgrid_table')) return 'zhengfang';
        if (url.indexOf('/student/') >= 0) return 'urp';
        if (document.querySelector('.displayTag')) return 'urp';
        if (document.querySelector('.wut_table') || document.querySelector('.mtt_arrange_item')) return 'jinzhi';
        return 'unknown';
    }

    // ================================================================
    //  智能过滤HTML（供LLM兜底）
    // ================================================================
    function getFilteredHtml(systemType) {
        // 第一层：系统特定提取
        var table = null;
        switch (systemType) {
            case 'qiangzhi':
                table = document.getElementById('timetable') || document.getElementById('kbtable');
                break;
            case 'zhengfang':
                table = document.getElementById('table1') || document.getElementById('Table1') || document.getElementById('kbgrid_table');
                break;
            case 'urp':
                table = document.querySelector('.displayTag');
                if (!table) {
                    var tables = document.querySelectorAll('table');
                    for (var i = 0; i < tables.length; i++) {
                        var text = tables[i].innerText || '';
                        if (text.indexOf('课程名') >= 0 && text.indexOf('星期') >= 0) {
                            table = tables[i];
                            break;
                        }
                    }
                }
                break;
            case 'jinzhi':
                table = document.querySelector('.wut_table');
                break;
        }

        if (table) {
            return cleanHtml(table.outerHTML);
        }

        // 第二层：通用查找课表表格
        var tables = document.querySelectorAll('table');
        var bestTable = null;
        var bestScore = 0;
        for (var i = 0; i < tables.length; i++) {
            var text = tables[i].innerText || '';
            var score = 0;
            if (text.indexOf('星期') >= 0) score += 10;
            if (text.indexOf('节') >= 0) score += 5;
            if (text.indexOf('课程') >= 0 || text.indexOf('周次') >= 0) score += 5;
            score += tables[i].querySelectorAll('tr').length;
            if (score > bestScore) {
                bestScore = score;
                bestTable = tables[i];
            }
        }

        if (bestTable && bestScore > 10) {
            return cleanHtml(bestTable.outerHTML);
        }

        // 第三层：返回body（清理后）
        return cleanHtml(document.body ? document.body.innerHTML : '');
    }

    function cleanHtml(html) {
        // 去掉script/style/nav/footer/header
        var cleaned = html
            .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
            .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
            .replace(/<nav[^>]*>[\s\S]*?<\/nav>/gi, '')
            .replace(/<footer[^>]*>[\s\S]*?<\/footer>/gi, '')
            .replace(/<header[^>]*>[\s\S]*?<\/header>/gi, '')
            .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
            .replace(/<!--[\s\S]*?-->/g, '');

        // 去掉img/svg/video标签
        cleaned = cleaned
            .replace(/<img[^>]*>/gi, '')
            .replace(/<svg[^>]*>[\s\S]*?<\/svg>/gi, '')
            .replace(/<video[^>]*>[\s\S]*?<\/video>/gi, '');

        // 去掉display:none的元素（简单匹配）
        cleaned = cleaned
            .replace(/<[^>]+style="[^"]*display\s*:\s*none[^"]*"[\s\S]*?<\/[^>]+>/gi, '')
            .replace(/<[^>]+style="[^"]*display\s*:\s*none[^"]*"[^>]*\/>/gi, '');

        // 去掉多余的空白
        cleaned = cleaned.replace(/\s+/g, ' ').trim();

        return cleaned;
    }

    // ================================================================
    //  强智解析器
    // ================================================================
    function parseQiangzhi() {
        var table = document.getElementById('timetable') || document.getElementById('kbtable');
        if (!table) return [];

        var courses = [];
        var rows = table.querySelectorAll('tr');
        var nodeCount = 0;

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var tds = row.querySelectorAll('td');
            if (tds.length === 0) continue;
            nodeCount++;

            var day = 0;
            for (var j = 0; j < tds.length; j++) {
                day++;
                var td = tds[j];
                var kbDivs = td.querySelectorAll('.kbcontent');

                for (var k = 0; k < kbDivs.length; k++) {
                    var div = kbDivs[k];
                    var divStyle = div.getAttribute('style') || '';
                    if (divStyle.indexOf('display:none') >= 0 || divStyle.indexOf('display: none') >= 0) continue;

                    var divText = (div.innerText || '').trim();
                    if (!divText || divText.length <= 2 || divText === '\u00a0') continue;

                    var html = div.innerHTML;
                    var parts = html.split(/-{5,}/);

                    for (var p = 0; p < parts.length; p++) {
                        var part = parts[p];
                        if (!part.trim()) continue;

                        var tmp = document.createElement('div');
                        tmp.innerHTML = part;

                        var name = '', teacher = '', room = '', weekStr = '';
                        var fonts = tmp.querySelectorAll('font');

                        for (var f = 0; f < fonts.length; f++) {
                            var font = fonts[f];
                            var fStyle = font.getAttribute('style') || '';
                            if (fStyle.indexOf('display:none') >= 0 || fStyle.indexOf('display: none') >= 0) continue;

                            var fText = (font.innerText || font.textContent || '').trim();
                            if (!fText || fText === '\u00a0') continue;

                            var title = font.getAttribute('title') || '';
                            if (title === '教师' || title === '老师') teacher = fText;
                            else if (title === '周次(节次)') weekStr = fText;
                            else if (title === '教室') room = fText;
                            else if (title === '教学楼') { if (!room) room = fText; }
                            else if (!title && !name) name = fText;
                        }

                        if (!name) continue;

                        var parsed = parseWeekPeriod(weekStr, nodeCount);
                        if (parsed.weeks.length > 0) {
                            courses.push({d:day, s:parsed.start, e:parsed.end, n:name, t:teacher, r:room, w:parsed.weeks, tp:parsed.type});
                        } else {
                            courses.push({d:day, s:parsed.start, e:parsed.end, raw:(name+'|'+teacher+'|'+weekStr+'|'+room).trim()});
                        }
                    }
                }
            }
        }
        return courses;
    }

    // ================================================================
    //  正方解析器链
    // ================================================================
    function parseZhengfang() {
        var courses = parseNewZhengfang();
        if (courses.length > 0) return courses;
        return parseOldZhengfang();
    }

    function parseNewZhengfang() {
        var table = document.getElementById('table1');
        if (!table) return [];

        var courses = [];
        var rows = table.querySelectorAll('tr');
        var node = 0;

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var festival = row.querySelector('.festival');
            if (!festival) continue;
            node = parseInt(festival.innerText || festival.textContent || '0');
            if (isNaN(node) || node <= 0) continue;

            var tds = row.querySelectorAll('td');
            for (var j = 0; j < tds.length; j++) {
                var td = tds[j];
                var tdId = td.getAttribute('id') || '';
                var day = 0;
                if (tdId.length > 0) day = parseInt(tdId.charAt(0));
                if (isNaN(day) || day <= 0) continue;

                var divs = td.querySelectorAll('div');
                for (var k = 0; k < divs.length; k++) {
                    var div = divs[k];
                    var courseName = div.querySelector('.title');
                    if (!courseName) continue;
                    var name = (courseName.innerText || '').trim();
                    if (!name) continue;

                    var teacher = '', room = '', weekStr = '';
                    var pList = div.querySelectorAll('p');
                    for (var pi = 0; pi < pList.length; pi++) {
                        var p = pList[pi];
                        var pTitle = p.getAttribute('title') || '';
                        var pText = (p.innerText || '').trim();
                        if (pTitle === '教师') teacher = pText;
                        else if (pTitle === '上课地点') room = pText;
                        else if (pTitle === '节/周' || pTitle === '周/节') weekStr = pText;
                    }

                    if (!weekStr) continue;

                    var parsed = parseWeekPeriod(weekStr, node);
                    if (parsed.weeks.length > 0) {
                        courses.push({d:day, s:parsed.start, e:parsed.end, n:name, t:teacher, r:room, w:parsed.weeks, tp:parsed.type});
                    } else {
                        courses.push({d:day, s:parsed.start, e:parsed.end, raw:(name+'|'+teacher+'|'+weekStr+'|'+room).trim()});
                    }
                }
            }
        }
        return courses;
    }

    function parseOldZhengfang() {
        var table = document.getElementById('Table1') || document.getElementById('kbgrid_table');
        if (!table) return [];

        var courses = [];
        var rows = table.querySelectorAll('tr');
        var node = 0;

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var tds = row.querySelectorAll('td');
            if (tds.length < 2) continue;

            var firstTd = tds[0];
            var firstText = (firstTd.innerText || '').trim();
            var nodeMatch = firstText.match(/第(\d+)节/);
            if (nodeMatch) {
                node = parseInt(nodeMatch[1]);
                continue;
            }

            for (var j = 1; j < tds.length; j++) {
                var td = tds[j];
                var day = j;
                var html = td.innerHTML;
                if (!html || html.trim().length < 10) continue;

                var parts = html.split(/(?:<br\s*\/?>){2,}/);
                for (var p = 0; p < parts.length; p++) {
                    var part = parts[p];
                    if (!part || part.trim().length < 5) continue;

                    var tmp = document.createElement('div');
                    tmp.innerHTML = part;
                    var text = (tmp.innerText || '').trim();
                    if (!text) continue;

                    var lines = text.split(/\n/).filter(function(l) { return l.trim().length > 0; });
                    if (lines.length < 2) continue;

                    var name = lines[0].trim();
                    var teacher = '', room = '', weekStr = '';

                    for (var li = 1; li < lines.length; li++) {
                        var line = lines[li].trim();
                        if (line.indexOf('周') >= 0 && (line.indexOf('节') >= 0 || line.indexOf('[') >= 0)) {
                            weekStr = line;
                        } else if (line.length <= 4 && !teacher) {
                            teacher = line;
                        } else if (!room) {
                            room = line;
                        }
                    }

                    if (!weekStr) continue;

                    var parsed = parseWeekPeriod(weekStr, node);
                    if (parsed.weeks.length > 0) {
                        courses.push({d:day, s:parsed.start, e:parsed.end, n:name, t:teacher, r:room, w:parsed.weeks, tp:parsed.type});
                    } else {
                        courses.push({d:day, s:parsed.start, e:parsed.end, raw:(name+'|'+teacher+'|'+weekStr+'|'+room).trim()});
                    }
                }
            }
        }
        return courses;
    }

    // ================================================================
    //  URP解析器
    // ================================================================
    function parseURP() {
        var tables = document.querySelectorAll('table');
        for (var t = 0; t < tables.length; t++) {
            var table = tables[t];
            var thead = table.querySelector('thead');
            if (!thead) continue;

            var headers = [];
            var ths = thead.querySelectorAll('th');
            for (var h = 0; h < ths.length; h++) {
                headers.push((ths[h].innerText || '').trim());
            }

            var nameIdx = -1, teacherIdx = -1, weekIdx = -1, dayIdx = -1, nodeIdx = -1, roomIdx = -1;
            for (var hi = 0; hi < headers.length; hi++) {
                var header = headers[hi];
                if (header === '课程名' || header === '课程名称') nameIdx = hi;
                else if (header === '教师' || header === '授课教师') teacherIdx = hi;
                else if (header === '周次') weekIdx = hi;
                else if (header === '星期') dayIdx = hi;
                else if (header === '节次') nodeIdx = hi;
                else if (header === '教室' || header === '上课地点') roomIdx = hi;
            }

            if (nameIdx < 0 || dayIdx < 0 || nodeIdx < 0) continue;

            var tbody = table.querySelector('tbody');
            if (!tbody) continue;
            var rows = tbody.querySelectorAll('tr');
            var courses = [];
            for (var r = 0; r < rows.length; r++) {
                var cells = rows[r].querySelectorAll('td');
                if (cells.length <= Math.max(nameIdx, dayIdx, nodeIdx)) continue;

                var name = (cells[nameIdx].innerText || '').trim();
                if (!name) continue;

                var day = parseInt((cells[dayIdx].innerText || '').trim().replace(/[^0-9]/g, ''));
                var nodeStr = (cells[nodeIdx].innerText || '').trim();
                var nodeParts = nodeStr.split('-');
                var startNode = parseInt(nodeParts[0]) || 1;
                var endNode = parseInt(nodeParts[nodeParts.length - 1]) || startNode;

                var teacher = teacherIdx >= 0 ? (cells[teacherIdx].innerText || '').trim() : '';
                var room = roomIdx >= 0 ? (cells[roomIdx].innerText || '').trim() : '';
                var weekStr = weekIdx >= 0 ? (cells[weekIdx].innerText || '').trim() : '';

                var parsed = parseWeekPeriod(weekStr, startNode);
                if (parsed.weeks.length > 0) {
                    courses.push({d:day, s:startNode, e:endNode, n:name, t:teacher, r:room, w:parsed.weeks, tp:parsed.type});
                } else {
                    courses.push({d:day, s:startNode, e:endNode, raw:(name+'|'+teacher+'|'+weekStr+'|'+room).trim()});
                }
            }
            if (courses.length > 0) return courses;
        }
        return [];
    }

    // ================================================================
    //  金智解析器
    // ================================================================
    function parseJinZhi() {
        var container = document.querySelector('.wut_table') || document.querySelector('.mtt_arrange_item');
        if (!container) return [];

        var courses = [];
        var items = document.querySelectorAll('.mtt_arrange_item');
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            var nameEl = item.querySelector('.mtt_item_kcmc');
            if (!nameEl) continue;
            var name = (nameEl.innerText || '').trim().replace(/^\S+\s+/, '');
            if (!name) continue;

            var teacherEl = item.querySelector('.mtt_item_jxbmc');
            var teacher = teacherEl ? (teacherEl.innerText || '').trim() : '';

            var roomEl = item.querySelector('.mtt_item_room');
            var roomText = roomEl ? (roomEl.innerText || '').trim() : '';
            var details = roomText.split(',').map(function(s) { return s.trim(); });

            var day = 0, startNode = 1, endNode = 1;
            var dayIdx = -1;
            for (var d = 0; d < details.length; d++) {
                if (details[d].indexOf('星期') >= 0) {
                    dayIdx = d;
                    day = parseInt(details[d].replace(/[^0-9]/g, '')) || 1;
                    break;
                }
            }

            if (dayIdx >= 0 && dayIdx + 1 < details.length) {
                var nodeInfo = details[dayIdx + 1].replace('节', '').split('-');
                startNode = parseInt(nodeInfo[0]) || 1;
                endNode = parseInt(nodeInfo[nodeInfo.length - 1]) || startNode;
            }

            var room = details.length > dayIdx + 2 ? details[details.length - 2] : (details[details.length - 1] || '');

            var weekStr = '';
            for (var wi = 0; wi < dayIdx; wi++) {
                if (details[wi].indexOf('周') >= 0 || details[wi].indexOf('-') >= 0) {
                    weekStr = details[wi];
                    break;
                }
            }

            var parsed = parseWeekPeriod(weekStr, startNode);
            if (parsed.weeks.length > 0) {
                courses.push({d:day, s:startNode, e:endNode, n:name, t:teacher, r:room, w:parsed.weeks, tp:parsed.type});
            } else {
                courses.push({d:day, s:startNode, e:endNode, raw:(name+'|'+teacher+'|'+weekStr+'|'+room).trim()});
            }
        }
        return courses;
    }

    // ================================================================
    //  通用工具函数
    // ================================================================
    function parseWeekPeriod(str, defaultNode) {
        var weeks = [];
        var type = 0;
        var startNode = defaultNode;
        var endNode = defaultNode;

        if (!str) return {weeks:weeks, start:startNode, end:endNode, type:type};

        if (str.indexOf('单') >= 0) type = 1;
        if (str.indexOf('双') >= 0) type = 2;

        var periodMatch = str.match(/[\[\(](\d+)-(\d+)(?:-(\d+))?(?:-(\d+))?节[\]\)]/);
        if (periodMatch) {
            startNode = parseInt(periodMatch[1]);
            var lastValid = periodMatch.length - 1;
            while (lastValid > 0 && !periodMatch[lastValid]) lastValid--;
            endNode = parseInt(periodMatch[lastValid]);
        }

        var weekPart = str;
        weekPart = weekPart.replace(/[\[\(]\d+-\d+(?:-\d+)*节[\]\)]/g, '');
        weekPart = weekPart.replace(/\(周\)/g, '');
        weekPart = weekPart.replace(/[单双]/g, '');
        weekPart = weekPart.replace(/^[^\d]*/, '').replace(/[^\d]*$/, '');

        if (!weekPart.trim()) return {weeks:weeks, start:startNode, end:endNode, type:type};

        var weekRanges = weekPart.split(/[,，]/);
        for (var w = 0; w < weekRanges.length; w++) {
            var wr = weekRanges[w].trim();
            if (!wr) continue;
            if (wr.indexOf('-') >= 0) {
                var parts = wr.split('-');
                var ws = parseInt(parts[0]);
                var we = parseInt(parts[1]);
                if (!isNaN(ws) && !isNaN(we)) {
                    for (var x = ws; x <= we; x++) weeks.push(x);
                }
            } else {
                var wn = parseInt(wr);
                if (!isNaN(wn)) weeks.push(wn);
            }
        }

        return {weeks:weeks, start:startNode, end:endNode, type:type};
    }
})()
"""
}
