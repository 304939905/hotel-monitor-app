import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/services/batch_hotel_parser.dart';

void main() {
  test('解析主体酒店和多家竞对酒店，并把 1 转为空名字', () {
    final result = parseBatchHotels('''
主体酒店
主体美团名
主体携程名

A
竞对A美团名
竞对A携程名

B
竞对B美团名
1
''');

    expect(result.mainHotel?.meituanName, '主体美团名');
    expect(result.mainHotel?.ctripName, '主体携程名');
    expect(result.competitors, hasLength(2));
    expect(result.competitors[0].meituanName, '竞对A美团名');
    expect(result.competitors[0].ctripName, '竞对A携程名');
    expect(result.competitors[1].meituanName, '竞对B美团名');
    expect(result.competitors[1].ctripName, isEmpty);
  });

  test('没有主体酒店段时只解析竞对酒店', () {
    final result = parseBatchHotels('''
A
1
竞对A携程名

F
大华酒店
1
''');

    expect(result.mainHotel, isNull);
    expect(result.competitors, hasLength(2));
    expect(result.competitors[0].meituanName, isEmpty);
    expect(result.competitors[0].ctripName, '竞对A携程名');
    expect(result.competitors[1].meituanName, '大华酒店');
    expect(result.competitors[1].ctripName, isEmpty);
  });

  test('拒绝信息不完整的竞对酒店', () {
    expect(
      () => parseBatchHotels('''
A
只有美团名字
'''),
      throwsA(
        isA<BatchHotelFormatException>().having(
          (error) => error.message,
          'message',
          contains('信息不完整'),
        ),
      ),
    );
  });

  test('拒绝两个平台名字都为空的酒店', () {
    expect(
      () => parseBatchHotels('''
A
1
1
'''),
      throwsA(
        isA<BatchHotelFormatException>().having(
          (error) => error.message,
          'message',
          contains('不能同时为空'),
        ),
      ),
    );
  });
}
