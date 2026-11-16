import '../models/monitor_project.dart';

const batchHotelTemplate = '''主体酒店
美团名称
携程名称

A
美团名称
携程名称

B
美团名称
携程名称

规则：
每组固定 3 行
竞对用 A/B/C……分组
不写主体酒店＝只加竞对
平台无名称填 1''';

class BatchHotelImport {
  final HotelConfig? mainHotel;
  final List<HotelConfig> competitors;

  const BatchHotelImport({this.mainHotel, required this.competitors});
}

class BatchHotelFormatException implements Exception {
  final String message;

  const BatchHotelFormatException(this.message);

  @override
  String toString() => message;
}

BatchHotelImport parseBatchHotels(String source) {
  final lines = source
      .replaceAll('\r\n', '\n')
      .replaceAll('\r', '\n')
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty)
      .toList();

  if (lines.isEmpty) {
    throw const BatchHotelFormatException('请粘贴或输入酒店信息');
  }

  String normalizeName(String value) => value == '1' ? '' : value;

  var cursor = 0;
  HotelConfig? mainHotel;
  if (lines.first == '主体酒店') {
    if (lines.length < 3) {
      throw const BatchHotelFormatException(
        '“主体酒店”下面需要依次填写美团名字和携程名字；没有名字请填写 1',
      );
    }
    final meituanName = normalizeName(lines[1]);
    final ctripName = normalizeName(lines[2]);
    if (meituanName.isEmpty && ctripName.isEmpty) {
      throw const BatchHotelFormatException('主体酒店的美团名字和携程名字不能同时为空');
    }
    mainHotel = HotelConfig.create(
      meituanName: meituanName,
      ctripName: ctripName,
    );
    cursor = 3;
  }

  final competitors = <HotelConfig>[];
  final groupPattern = RegExp(r'^[A-Za-z]+$');
  while (cursor < lines.length) {
    final group = lines[cursor];
    if (!groupPattern.hasMatch(group)) {
      throw BatchHotelFormatException(
        '第 ${cursor + 1} 行“$group”应为竞对酒店分组字母，例如 A、B、C',
      );
    }
    if (cursor + 2 >= lines.length) {
      throw BatchHotelFormatException(
        '竞对酒店 $group 信息不完整：字母下面需要依次填写美团名字和携程名字；没有名字请填写 1',
      );
    }

    final meituanName = normalizeName(lines[cursor + 1]);
    final ctripName = normalizeName(lines[cursor + 2]);
    if (meituanName.isEmpty && ctripName.isEmpty) {
      throw BatchHotelFormatException('竞对酒店 $group 的两个平台名字不能同时为空');
    }
    competitors.add(
      HotelConfig.create(meituanName: meituanName, ctripName: ctripName),
    );
    cursor += 3;
  }

  if (mainHotel == null && competitors.isEmpty) {
    throw const BatchHotelFormatException('没有识别到可添加的酒店');
  }

  return BatchHotelImport(mainHotel: mainHotel, competitors: competitors);
}
