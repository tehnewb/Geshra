import {createElement, memo} from 'react';

/**
 * Renders one stable keyed native row. Ordinary React memoization avoids re-rendering unchanged
 * rows when a tenth-row update or selection replaces only the affected row objects.
 */
function MatchedRow({row}) {
    return createElement('tr', {'data-row': row.id, className: row.selected ? 'selected' : undefined}, createElement('td', null, row.id), createElement('td', null, row.label));
}

/**
 * Standard memoized row component, without a custom comparator or compiler transformation.
 */
const Row = memo(MatchedRow);

/**
 * Keeps counter/form/status updates from recreating table elements when row state is unchanged.
 */
function MatchedTable({rows}) {
    return createElement(
        'table',
        null,
        createElement(
            'tbody',
            null,
            rows.map((row) => createElement(Row, {key: row.id, row}))
        )
    );
}

/**
 * Standard memoized native table; all rows remain mounted and none are virtualized.
 */
export const Table = memo(MatchedTable);
