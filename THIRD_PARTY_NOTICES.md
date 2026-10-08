# Geshra third party notices

The [MIT license](LICENSE) applies to Geshra's original code. Bundled third party software retains its own copyright and license terms.

## Highcharts assets

The following files contain software from Highsoft AS:

| Files | Software | License information |
| --- | --- | --- |
| `src/main/resources/web/js/highcharts.js` | Highcharts Core 12.2.0 | [Highcharts license](https://www.highcharts.com/license) |
| `src/main/resources/web/js/stockcharts.js` | Highcharts Stock 12.2.0 | [Highcharts license](https://www.highcharts.com/license) |
| `src/main/resources/web/js/grid-lite.js`, `src/main/resources/web/css/grid.css` | Highcharts Grid Lite 1.1.0 | [Grid Lite license](https://shop.highcharts.com/license-grid-lite-1.0) |

These assets are optional browser resources. Geshra's runtime and native Java UI components do not load them automatically. Geshra's MIT license does not grant a Highcharts license; applications using these assets must follow the corresponding vendor terms.

## Java dependencies

The published Maven metadata lists the library's Java dependencies. Those dependencies retain their own licenses. Browser test dependencies are used only for development and are not added to the published Java library dependencies.
