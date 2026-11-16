import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/models/monitor_project.dart';
import 'package:hotel_monitor_app/services/collection_service.dart';

void main() {
  const service = MockCollectionService(delay: Duration.zero);

  test('只采集已选中的平台，并覆盖主体和竞对酒店', () async {
    final project = MonitorProject(
      name: '测试项目',
      city: '上海',
      mainHotel: HotelConfig(
        id: 'main',
        meituanName: '美团主体酒店',
        ctripName: '携程主体酒店',
      ),
      competitors: [
        HotelConfig(
          id: 'competitor',
          meituanName: '美团竞对酒店',
          ctripName: '携程竞对酒店',
        ),
      ],
      meituanSelected: true,
      ctripSelected: false,
    );
    await service.collect(project);
    expect(project.meituanReports.keys, containsAll(['main', 'competitor']));
    expect(project.meituanReports['main']!.current!.hotelName, '美团主体酒店');
    expect(project.ctripReports, isEmpty);
  });

  test('第二次采集时保留上一次汇报', () async {
    final project = MonitorProject(
      name: '测试项目',
      city: '上海',
      mainHotel: HotelConfig(
        id: 'main',
        meituanName: '美团主体酒店',
        ctripName: '携程主体酒店',
      ),
      competitors: [],
      meituanSelected: false,
      ctripSelected: true,
    );
    await service.collect(project);
    final firstReport = project.ctripReports['main']!.current;
    await service.collect(project);
    final history = project.ctripReports['main']!;
    expect(history.current!.hotelName, '携程主体酒店');
    expect(history.previous, same(firstReport));
    expect(history.current, isNot(same(firstReport)));
  });

  test('竞对酒店的平台名称为空时跳过该平台', () async {
    final project = MonitorProject(
      name: '测试项目',
      city: '上海',
      mainHotel: HotelConfig(
        id: 'main',
        meituanName: '美团主体酒店',
        ctripName: '携程主体酒店',
      ),
      competitors: [
        HotelConfig(id: 'ctrip-only', meituanName: '', ctripName: '仅携程酒店'),
        HotelConfig(id: 'meituan-only', meituanName: '仅美团酒店', ctripName: ''),
      ],
    );

    await service.collect(project);

    expect(project.meituanReports, contains('meituan-only'));
    expect(project.meituanReports, isNot(contains('ctrip-only')));
    expect(project.ctripReports, contains('ctrip-only'));
    expect(project.ctripReports, isNot(contains('meituan-only')));
  });
}
