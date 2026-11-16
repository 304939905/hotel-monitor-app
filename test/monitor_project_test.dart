import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/models/monitor_project.dart';

void main() {
  test('项目 JSON 往返后保留配置和汇报历史', () {
    final createdAt = DateTime(2026, 7, 15, 10, 30);
    final project = MonitorProject(
      name: '上海监控',
      city: '上海',
      mainHotel: HotelConfig(
        id: 'main-hotel',
        meituanName: '美团主体酒店',
        ctripName: '携程主体酒店',
      ),
      competitors: [
        HotelConfig(
          id: 'competitor-hotel',
          meituanName: '美团竞对酒店',
          ctripName: '携程竞对酒店',
        ),
      ],
      meituanSelected: true,
      ctripSelected: false,
      createdAt: createdAt,
      todayViewCount: 3,
      lastViewDate: createdAt,
      meituanReports: {
        'main-hotel': HotelReportHistory(
          current: ReportData(
            time: createdAt,
            platform: '美团',
            hotelName: '主体酒店',
            roomNames: const ['大床房'],
            resultStatus: 'full',
          ),
        ),
      },
    );
    final restored = MonitorProject.fromJson(project.toJson());
    expect(restored.name, project.name);
    expect(restored.competitors.single.meituanName, '美团竞对酒店');
    expect(restored.competitors.single.ctripName, '携程竞对酒店');
    expect(restored.createdAt, createdAt);
    expect(restored.todayViewCount, 3);
    expect(restored.ctripSelected, isFalse);
    expect(restored.meituanReports['main-hotel']!.current!.roomNames, ['大床房']);
    expect(
      restored.meituanReports['main-hotel']!.current!.effectiveStatus,
      'full',
    );
  });

  test('旧版单一酒店名称自动迁移为两个平台名称', () {
    final restored = MonitorProject.fromJson({
      'name': '旧项目',
      'city': '上海',
      'mainHotel': '旧主体酒店',
      'competitors': ['旧竞对酒店'],
    });

    expect(restored.mainHotel.meituanName, '旧主体酒店');
    expect(restored.mainHotel.ctripName, '旧主体酒店');
    expect(restored.competitors.single.meituanName, '旧竞对酒店');
    expect(restored.competitors.single.ctripName, '旧竞对酒店');
  });

  test('单平台竞对酒店可以保留空名称并正确显示', () {
    final ctripOnly = HotelConfig(
      id: 'ctrip-only',
      meituanName: '',
      ctripName: '仅携程酒店',
    );

    expect(ctripOnly.displayName, '仅携程酒店');
    expect(ctripOnly.hasNameForPlatform('携程'), isTrue);
    expect(ctripOnly.hasNameForPlatform('美团'), isFalse);

    final restored = HotelConfig.fromJson(ctripOnly.toJson());
    expect(restored.meituanName, isEmpty);
    expect(restored.ctripName, '仅携程酒店');
  });
}
