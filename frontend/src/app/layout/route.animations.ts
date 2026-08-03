import {
  trigger,
  transition,
  style,
  query,
  group,
  animate
} from '@angular/animations';

export const routeAnimations = trigger('routeAnimations', [
  transition('* <=> *', [
    query(
      ':enter',
      [
        style({
          opacity: 0,
          transform: 'translateY(18px) scale(0.99)',
          filter: 'blur(4px)'
        })
      ],
      { optional: true }
    ),
    query(
      ':leave',
      [
        style({
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          opacity: 1,
          transform: 'translateY(0) scale(1)',
          filter: 'blur(0)'
        })
      ],
      { optional: true }
    ),
    group([
      query(
        ':leave',
        [
          animate(
            '220ms ease-in',
            style({
              opacity: 0,
              transform: 'translateY(-10px) scale(0.99)',
              filter: 'blur(4px)'
            })
          )
        ],
        { optional: true }
      ),
      query(
        ':enter',
        [
          animate(
            '420ms 80ms cubic-bezier(0.22, 1, 0.36, 1)',
            style({
              opacity: 1,
              transform: 'translateY(0) scale(1)',
              filter: 'blur(0)'
            })
          )
        ],
        { optional: true }
      )
    ])
  ])
]);
