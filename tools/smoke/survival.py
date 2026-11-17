import ctypes as c, time, subprocess
from pathlib import Path
x=c.CDLL('libX11.so.6'); t=c.CDLL('libXtst.so.6')
x.XOpenDisplay.argtypes=[c.c_char_p]; x.XOpenDisplay.restype=c.c_void_p
x.XStringToKeysym.argtypes=[c.c_char_p]; x.XStringToKeysym.restype=c.c_ulong
x.XKeysymToKeycode.argtypes=[c.c_void_p,c.c_ulong];x.XKeysymToKeycode.restype=c.c_uint
x.XSetInputFocus.argtypes=[c.c_void_p,c.c_ulong,c.c_int,c.c_ulong]
x.XFlush.argtypes=[c.c_void_p]
t.XTestFakeKeyEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeButtonEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeMotionEvent.argtypes=[c.c_void_p,c.c_int,c.c_int,c.c_int,c.c_ulong]
d=x.XOpenDisplay(b':98'); assert d
window=int(subprocess.check_output(['xwininfo','-display',':98','-root','-tree'],text=True).split('"Cubic Dawn"')[0].split()[-1],16)
x.XSetInputFocus(d,window,1,0);x.XFlush(d)
def key(name,hold=.20):
 code=x.XKeysymToKeycode(d,x.XStringToKeysym(name.encode()))
 t.XTestFakeKeyEvent(d,code,1,0); x.XFlush(d);time.sleep(hold)
 t.XTestFakeKeyEvent(d,code,0,0); x.XFlush(d);time.sleep(.25)
def click(button=1):
 t.XTestFakeButtonEvent(d,button,1,0);x.XFlush(d);time.sleep(.2)
 t.XTestFakeButtonEvent(d,button,0,0);x.XFlush(d);time.sleep(.3)
def shot(name):
 output=Path('build/ui-smoke');output.mkdir(parents=True,exist_ok=True)
 subprocess.run(['import','-display',':98','-window','root',str(output/(name+'.png'))],check=True)
def items():
 return {int(a[1]):(int(a[2]),int(a[3])) for a in (l.split() for l in Path('build/survival-smoke.txt').read_text().splitlines()) if a[0]=='item'}


time.sleep(2);key('F5');shot('survival-death')
text=Path('build/survival-smoke.txt').read_text()
p=next(l.split() for l in text.splitlines() if l.startswith('player '));assert int(p[7])==0,p
key('r');key('F5');shot('survival-respawn')
text=Path('build/survival-smoke.txt').read_text()
p=next(l.split() for l in text.splitlines() if l.startswith('player '));assert int(p[7])==20,p
assert 'item 0 201 1 7' in text and '17:3:0' in text,text
key('Escape');key('q')
print('PASS UI_FALL_DEATH SAFE_RESPAWN INVENTORY_WEAR_CHEST_KEPT')
