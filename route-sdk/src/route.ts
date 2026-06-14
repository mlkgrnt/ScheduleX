/**
 * ScheduleX Route SDK - Base Route Class
 *
 * 所有路由必须继承 BaseRoute 并实现抽象方法。
 * SDK 会自动处理 JSBridge 通信和错误处理。
 */

import type {
  RouteMeta,
  RouteContext,
  Course,
  SemesterInfo,
  LoginResult,
} from './types';

export abstract class BaseRoute {
  /** 路由元信息，子类必须实现 */
  abstract readonly meta: RouteMeta;

  /** 路由上下文，init() 后可用 */
  protected ctx!: RouteContext;

  /**
   * 初始化路由（由 SDK 自动调用）
   * 子类可以覆盖此方法来执行初始化逻辑
   */
  init(context: RouteContext): void {
    this.ctx = context;
  }

  /**
   * 登录教务系统
   * @param username 学号/用户名
   * @param password 密码
   * @param schoolIndex 学校索引（当路由支持多所学校时）
   */
  abstract login(
    username: string,
    password: string,
    schoolIndex?: number
  ): Promise<LoginResult>;

  /**
   * 获取学期列表
   */
  abstract getSemesters(): Promise<SemesterInfo[]>;

  /**
   * 获取课程列表
   * @param semesterId 学期ID（从 getSemesters 返回）
   */
  abstract getCourses(semesterId: string): Promise<Course[]>;

  /**
   * 获取教务系统域名（子类可覆盖以支持多学校）
   */
  protected getDomain(schoolIndex?: number): string {
    const schools = this.meta.schools;
    if (schools.length === 0) {
      throw new Error('No schools configured');
    }
    const index = schoolIndex ?? 0;
    if (index < 0 || index >= schools.length) {
      throw new Error(`Invalid school index: ${index}`);
    }
    return schools[index].domain;
  }

  /**
   * 生成课程唯一颜色（基于课程名称哈希）
   */
  protected generateColor(name: string): string {
    const colors = [
      '#EF5350', '#EC407A', '#AB47BC', '#42A5F5',
      '#26C6DA', '#66BB6A', '#FFA726', '#8D6E63',
    ];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
  }

  /**
   * 解析周次字符串，如 "1-16周" -> [1,2,...,16]
   * 支持格式：
   *   "1-16周"     -> [1..16]
   *   "1,3,5,7周"  -> [1,3,5,7]
   *   "1-8,10-16周" -> [1..8, 10..16]
   *   "1-15(单)周"  -> [1,3,5,7,9,11,13,15]
   *   "2-16(双)周"  -> [2,4,6,8,10,12,14,16]
   */
  protected parseWeeks(weekStr: string): { weeks: number[]; type: 'ALL' | 'ODD' | 'EVEN' } {
    let str = weekStr.replace(/周/g, '').trim();
    let type: 'ALL' | 'ODD' | 'EVEN' = 'ALL';

    if (str.includes('单')) {
      type = 'ODD';
      str = str.replace(/[()（）单]/g, '');
    } else if (str.includes('双')) {
      type = 'EVEN';
      str = str.replace(/[()（）双]/g, '');
    }

    const weeks: number[] = [];
    const parts = str.split(/[,，]/);

    for (const part of parts) {
      const trimmed = part.trim();
      if (trimmed.includes('-')) {
        const [start, end] = trimmed.split('-').map(Number);
        if (!isNaN(start) && !isNaN(end)) {
          for (let i = start; i <= end; i++) {
            if (type === 'ALL') weeks.push(i);
            else if (type === 'ODD' && i % 2 === 1) weeks.push(i);
            else if (type === 'EVEN' && i % 2 === 0) weeks.push(i);
          }
        }
      } else {
        const num = Number(trimmed);
        if (!isNaN(num)) weeks.push(num);
      }
    }

    return { weeks, type };
  }
}
