import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('play_release', ROOT / 'scripts' / 'play_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class Version(unittest.TestCase):
    def test_reads_repository_version(self):
        name, code = release.read_version(ROOT / 'app' / 'build.gradle.kts')
        self.assertRegex(name, r'^\d+\.\d+\.\d+$')
        self.assertGreater(code, 0)

    def test_rejects_missing_or_unstable_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            gradle = Path(tmp) / 'build.gradle.kts'
            gradle.write_text('versionCode = 3\n')
            with self.assertRaises(SystemExit):
                release.read_version(gradle)
            gradle.write_text('versionCode = 3\nversionName = "1.2.0-beta"\n')
            with self.assertRaises(SystemExit):
                release.read_version(gradle)


class Notes(unittest.TestCase):
    def test_plain_text_is_en_us_and_escapes_become_newlines(self):
        self.assertEqual(release.parse_notes('Fixed A.\\nFixed B.'), {'en-US': 'Fixed A.\nFixed B.'})

    def test_play_console_tags(self):
        raw = '<en-US>\nFixed.\n</en-US>\n<ja-JP>\n修正しました。\n</ja-JP>'
        self.assertEqual(release.parse_notes(raw), {'en-US': 'Fixed.', 'ja-JP': '修正しました。'})

    def test_single_line_tags_from_web_form(self):
        self.assertEqual(release.parse_notes('<en-US>A\\nB</en-US><ko-KR>C</ko-KR>'),
                         {'en-US': 'A\nB', 'ko-KR': 'C'})

    def test_repository_notes_files_parse(self):
        files = sorted((ROOT / 'docs' / 'play-store').glob('release-notes-1.1.*.txt'))
        self.assertTrue(files)
        notes = release.parse_notes('@' + str(files[-1].relative_to(ROOT)), ROOT)
        self.assertEqual(set(notes), {'en-US', 'ja-JP', 'zh-CN', 'ko-KR'})

    def test_file_reference_outside_play_store_dir_is_rejected(self):
        with self.assertRaises(SystemExit):
            release.parse_notes('@README.md', ROOT)
        with self.assertRaises(SystemExit):
            release.parse_notes('@docs/play-store/../../README.md', ROOT)

    def test_rejects_bad_notes(self):
        for raw in ('', '<en-US></en-US>', '<en-US>a</en-US>stray', '<en-US>a</en-US><en-US>b</en-US>',
                    'x' * (release.PLAY_NOTES_LIMIT + 1)):
            with self.subTest(raw=raw[:20]), self.assertRaises(SystemExit):
                release.parse_notes(raw)


class Changelog(unittest.TestCase):
    def entry(self, code):
        return {'versionName': '1.2.0', 'versionCode': code, 'track': 'internal', 'releaseNotes': {'en-US': 'x'}}

    def test_appends_and_refuses_duplicate_version_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'changelog.json'
            release.check_new(path, 16)
            release.append_changelog(path, self.entry(16))
            release.append_changelog(path, self.entry(17))
            self.assertEqual([e['versionCode'] for e in json.loads(path.read_text())], [16, 17])
            with self.assertRaises(SystemExit):
                release.check_new(path, 16)
            with self.assertRaises(SystemExit):
                release.append_changelog(path, dict(self.entry(16), track='production'))

    def test_changelog_command_writes_entry(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / 'notes.json').write_text(json.dumps({'ja-JP': '修正'}, ensure_ascii=False))
            release.main(['changelog', '--changelog', str(tmp / 'c.json'), '--entry-out', str(tmp / 'e.json'),
                          '--notes', str(tmp / 'notes.json'), '--version-name', '1.2.0', '--version-code', '16',
                          '--track', 'production', '--status', 'draft', '--released-at', '2026-11-04T00:00:00Z',
                          '--commit', 'abc'])
            entry = json.loads((tmp / 'e.json').read_text())
            self.assertEqual(entry['releaseNotes'], {'ja-JP': '修正'})
            self.assertEqual(json.loads((tmp / 'c.json').read_text()), [entry])

    def test_repository_changelog_is_valid(self):
        entries = release.load_changelog(ROOT / 'docs' / 'releases' / 'changelog.json')
        codes = [e['versionCode'] for e in entries]
        self.assertEqual(len(codes), len(set(codes)))


if __name__ == '__main__':
    unittest.main()
