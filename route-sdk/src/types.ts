/**
 * ScheduleX Route SDK - Type Definitions
 *
 * 路由开发者只需要实现 BaseRoute 接口即可。
 * 所有与原生层的通信通过 RouteContext 完成。
 */

// ========== 路由元信息 ==========

export interface RouteMeta {
  id: string;               // 唯一标识，如 "zhengfang"
  name: string;             // 显示名称，如 "正方教务系统"
  author: string;           // 作者
  version: string;          // 版本号，如 "1.0.0"
  description: string;      // 描述
  schools: SchoolInfo[];    // 支持的学校列表
}

export interface SchoolInfo {
  name: string;             // 学校名称
  domain: string;           // 教务系统域名
  city?: string;            // 所在城市
}

// ========== 数据模型 ==========

export interface Course {
  id: string;
  name: string;
  teacher: string;
  location: string;
  color?: string;
  credit?: number;
  courseId?: string;         // 教务系统中的课程ID
  schedule: Schedule[];
}

export interface Schedule {
  day: number;              // 星期几 (1-7, 1=周一)
  start: number;            // 开始节次
  end: number;              // 结束节次
  weeks: number[];          // 周次列表 [1,2,3,5,7,9,11,13,15]
  type?: 'ALL' | 'ODD' | 'EVEN';
}

export interface SemesterInfo {
  id: string;               // 学期ID
  name: string;             // 学期名称，如 "2024-2025 第一学期"
}

export interface LoginResult {
  success: boolean;
  message?: string;
  studentName?: string;     // 登录成功后的学生姓名
}

// ========== 路由上下文（JSBridge 通信） ==========

/**
 * RouteContext 由原生层注入，提供与 Android 端通信的能力。
 * 路由代码通过 ctx 调用原生功能（网络请求、Toast 等）。
 */
export interface RouteContext {
  /**
   * 发送 HTTP 请求（经过原生层代理，支持 Cookie 管理）
   */
  http(options: HttpOptions): Promise<HttpResponse>;

  /**
   * 显示 Toast 消息
   */
  toast(message: string): void;

  /**
   * 获取存储的值
   */
  storage: RouteStorage;

  /**
   * 解析 HTML（使用原生解析器，比 DOMParser 更可靠）
   */
  parseHtml(html: string): Promise<ParsedDocument>;
}

export interface HttpOptions {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers?: Record<string, string>;
  body?: string | Record<string, string>;
  /** 是否自动跟随重定向，默认 true */
  followRedirects?: boolean;
  /** 超时时间（毫秒），默认 30000 */
  timeout?: number;
}

export interface HttpResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
  /** 最终 URL（经过重定向后） */
  url: string;
}

export interface RouteStorage {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export interface ParsedDocument {
  /**
   * 使用 CSS 选择器查询元素
   */
  querySelector(selector: string): ParsedElement | null;
  querySelectorAll(selector: string): ParsedElement[];
}

export interface ParsedElement {
  textContent: string;
  innerHTML: string;
  getAttribute(name: string): string | null;
  querySelector(selector: string): ParsedElement | null;
  querySelectorAll(selector: string): ParsedElement[];
}
