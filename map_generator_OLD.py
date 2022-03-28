from PIL import Image

# each tuple is (collumn offset: x, row offset: y)
# collumn offset determines in what order pixels are sent to the LED driver ship
# row offset determines which LEDs are selected by the shift register

matrix = (((0,0),(0,1),(0,2),(0,3),(0,4),(0,5),(0,6),(0,7),(),(),(),(0,8),(),   (),   (),   (),   (),   (),   ()   ),
          ((),   (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(),(),(),(1,8),(1,0),(),   (),   (),   (),   (),   ()   ),
          ((),   (),   (2,2),(2,3),(2,4),(2,5),(2,6),(2,7),(),(),(),(2,8),(2,0),(2,1),(),   (),   (),   (),   ()   ),
          ((),   (),   (),   (3,3),(3,4),(3,5),(3,6),(3,7),(),(),(),(3,8),(3,0),(3,1),(3,2),(),   (),   (),   ()   ),
          ((),   (),   (),   (),   (4,4),(4,5),(4,6),(4,7),(),(),(),(4,8),(4,0),(4,1),(4,2),(4,3),(),   (),   ()   ),
          ((),   (),   (),   (),   (),   (5,5),(5,6),(5,7),(),(),(),(5,8),(5,0),(5,1),(5,2),(5,3),(5,4),(),   ()   ),
          ((),   (),   (),   (),   (),   (),   (6,6),(6,7),(),(),(),(6,8),(6,0),(6,1),(6,2),(6,3),(6,4),(6,5),()   ),
          ((),   (),   (),   (),   (),   (),   (),   (7,7),(),(),(),(7,8),(7,0),(7,1),(7,2),(7,3),(7,4),(7,5),(7,6)))

# 32x8 matrices is 592 x 85
# 85 * 2 + 3 = 173
# extend 88 more for double the height
# 592 + 88 = 680
img = Image.new("RGB", (592, 173), (255, 255, 255)) # default to white background

pixels = img.load()

# total 32x16 matrices

numleds = 0;

for row in range(16):
    basey = 11 * row # basex, basey are coordinates for the top right corner of where the matrix should be       
    for collumn in range(32):
        basex = basey + collumn * 16
        if row > 7:
            basex -= 88

        y = 0
        for u in matrix:
            x = 0
            for v in u: # v goes through each tuple in matrix
                if v: # if v is empty, skip 
                    c = collumn * 9 + v[0]
                    b = c & 0xff # last byte
                    c = c >> 8   
                    g = c & 0xff # second to last byte
                    numleds += 1
                    try:
                        pixels[basex + x, basey + y] = (row * 8 + v[1], g, b)
                    except:
                        print("Out of bounds: " + str(basex + x) + ", " + str(basey + y))
                    # red is row, blue + green is a 16 bit int representing collumn

                x += 1
            y += 1


# paste the generated image into full 640 x 360 image
img2 = Image.new("RGB", (640, 360), (255, 255, 255)) # default to white background

offset = ((img2.size[0] - img.size[0]) // 2, (img2.size[1] - img.size[1]) // 2)
img2.paste(img, offset)

print(str(numleds) + " leds / active pixels in total")

img2.show()
img2.save("map.bmp")


