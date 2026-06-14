/**
 * ScheduleX Route SDK - Entry Point
 *
 * 路由文件应该 export default 一个 BaseRoute 的子类实例。
 * SDK 会自动调用 init() 并注入 RouteContext。
 *
 * 使用示例：
 * ```typescript
 * import { BaseRoute, RouteMeta, LoginResult, Course, SemesterInfo } from '@schedulex/route-sdk';
 *
 * class MyRoute extends BaseRoute {
 *   readonly meta: RouteMeta = { ... };
 *
 *   async login(username: string, password: string): Promise<LoginResult> {
 *     // 登录逻辑
 *   }
 *
 *   async getSemesters(): Promise<SemesterInfo[]> {
 *     // 获取学期列表
 *   }
 *
 *   async getCourses(semesterId: string): Promise<Course[]> {
 *     // 获取课程列表
 *   }
 * }
 *
 * export default new MyRoute();
 * ```
 */

export { BaseRoute } from './route';
export type {
  RouteMeta,
  SchoolInfo,
  Course,
  Schedule,
  SemesterInfo,
  LoginResult,
  RouteContext,
  HttpOptions,
  HttpResponse,
  RouteStorage,
  ParsedDocument,
  ParsedElement,
} from './types';
