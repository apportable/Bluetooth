#import <Foundation/NSArray.h>

@interface BluetoothReadQueue : NSObject
- (id)init;
- (void)enqueue:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag;
- (NSUInteger)nextLength;
- (long)nextTag;
- (NSUInteger)dequeue;
- (BOOL)isEmpty;
@end
